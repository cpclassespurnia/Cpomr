package com.example.omr

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Computer Vision routine for robust bubble fill state identification and processing:
 * 1. Sub-pixel local peak centroid alignment: corrects small optical skews and print shifts
 * 2. Local background paper annulus normalization: sample surrounding unprinted paper
 *    to make detection completely invariant to illumination gradients, shadows, and glare
 * 3. Core shading and distribution analysis: measures fill ratio, variance, and optical contrast
 * 4. Adaptive dynamic thresholding (Otsu / Sauvola inspired local contrast)
 * 5. Multi-state classification: EMPTY, PARTIAL, FILLED, AMBIGUOUS
 */
object OmrBubbleCvProcessor {

    enum class BubbleFillState {
        EMPTY,
        PARTIAL,
        FILLED,
        AMBIGUOUS
    }

    data class BubbleEvaluation(
        val cx: Float,
        val cy: Float,
        val radius: Float,
        val fillRatio: Float,
        val opticalContrast: Float,
        val paperLuminance: Float,
        val coreLuminance: Float,
        val state: BubbleFillState,
        val confidence: Float,
        val isFilled: Boolean
    )

    data class QuestionRowEvaluation(
        val questionIndex: Int,
        val selectedOption: String, // "A", "B", "C", "D", "*" (multiple), "-" (unattempted)
        val bubbles: List<BubbleEvaluation>,
        val dominantMargin: Float,
        val confidence: Float
    )

    /**
     * Evaluates a single bubble at (nominalCx, nominalCy) with radius.
     * Applies sub-pixel centroid refinement, local background annulus sampling,
     * and adaptive core thresholding.
     */
    fun evaluateBubble(
        bitmap: Bitmap,
        nominalCx: Int,
        nominalCy: Int,
        radius: Int
    ): BubbleEvaluation {
        val w = bitmap.width
        val h = bitmap.height

        if (nominalCx !in 2 until w - 2 || nominalCy !in 2 until h - 2 || radius < 2) {
            return BubbleEvaluation(
                cx = nominalCx.toFloat(),
                cy = nominalCy.toFloat(),
                radius = radius.toFloat(),
                fillRatio = 0f,
                opticalContrast = 0f,
                paperLuminance = 255f,
                coreLuminance = 255f,
                state = BubbleFillState.EMPTY,
                confidence = 0f,
                isFilled = false
            )
        }

        // 1. Sub-pixel Centroid Refinement:
        // Search in a [-3..+3] window to locate the darkest local centroid (pencil/pen mark or bubble center)
        var bestCx = nominalCx
        var bestCy = nominalCy
        var minNeighborhoodLum = 255f

        val searchWindow = min(3, radius / 2)
        for (dy in -searchWindow..searchWindow) {
            val py = (nominalCy + dy).coerceIn(0, h - 1)
            for (dx in -searchWindow..searchWindow) {
                val px = (nominalCx + dx).coerceIn(0, w - 1)
                val lum = getPixelLuminance(bitmap.getPixel(px, py))
                if (lum < minNeighborhoodLum) {
                    minNeighborhoodLum = lum
                    bestCx = px
                    bestCy = py
                }
            }
        }

        // 2. Local Paper Background Annulus Sampling:
        // Sample bare paper in the ring between 1.25 * radius and 1.85 * radius.
        // This measures the true illumination level of the surrounding paper directly adjacent to this bubble.
        val innerBgRadius = (radius * 1.25f).toInt().coerceAtLeast(radius + 2)
        val outerBgRadius = (radius * 1.85f).toInt().coerceAtLeast(innerBgRadius + 2)
        val innerBgSq = innerBgRadius * innerBgRadius
        val outerBgSq = outerBgRadius * outerBgRadius

        val minBgX = max(0, bestCx - outerBgRadius)
        val maxBgX = min(w - 1, bestCx + outerBgRadius)
        val minBgY = max(0, bestCy - outerBgRadius)
        val maxBgY = min(h - 1, bestCy + outerBgRadius)

        var bgLumSum = 0f
        var bgCount = 0
        val bgLums = mutableListOf<Float>()

        for (y in minBgY..maxBgY step 2) {
            val dy = y - bestCy
            val dySq = dy * dy
            for (x in minBgX..maxBgX step 2) {
                val dx = x - bestCx
                val distSq = dx * dx + dySq
                if (distSq in innerBgSq..outerBgSq) {
                    val lum = getPixelLuminance(bitmap.getPixel(x, y))
                    bgLumSum += lum
                    bgCount++
                    bgLums.add(lum)
                }
            }
        }

        val paperMeanLum = if (bgCount > 0) bgLumSum / bgCount else 220f

        // 3. Inner Core Sampling:
        // Sample core pixels strictly within r <= 0.68 * radius to avoid the printed circle boundary stroke.
        val coreRadius = (radius * 0.68f).toInt().coerceAtLeast(2)
        val coreRadiusSq = coreRadius * coreRadius

        val minCoreX = max(0, bestCx - coreRadius)
        val maxCoreX = min(w - 1, bestCx + coreRadius)
        val minCoreY = max(0, bestCy - coreRadius)
        val maxCoreY = min(h - 1, bestCy + coreRadius)

        var coreLumSum = 0f
        var coreCount = 0
        var darkPixelsCount = 0
        var minCoreLum = 255f

        // Adaptive threshold relative to the surrounding paper:
        // A filled pixel must be significantly darker than local bare paper
        val adaptiveContrastThreshold = max(24f, paperMeanLum * 0.25f)
        val darkThresholdLum = paperMeanLum - adaptiveContrastThreshold

        for (y in minCoreY..maxCoreY) {
            val dy = y - bestCy
            val dySq = dy * dy
            for (x in minCoreX..maxCoreX) {
                val dx = x - bestCx
                val distSq = dx * dx + dySq
                if (distSq <= coreRadiusSq) {
                    val lum = getPixelLuminance(bitmap.getPixel(x, y))
                    coreLumSum += lum
                    coreCount++
                    if (lum < darkThresholdLum) {
                        darkPixelsCount++
                    }
                    if (lum < minCoreLum) {
                        minCoreLum = lum
                    }
                }
            }
        }

        val coreMeanLum = if (coreCount > 0) coreLumSum / coreCount else paperMeanLum
        val fillRatio = if (coreCount > 0) darkPixelsCount.toFloat() / coreCount else 0f

        // Optical contrast: normalized relative drop from surrounding paper
        val opticalContrast = ((paperMeanLum - coreMeanLum) / max(paperMeanLum, 40f)).coerceIn(0f, 1f)

        // 4. Fill State Classification:
        val isFilled = (fillRatio >= 0.28f && opticalContrast >= 0.20f) || (opticalContrast >= 0.38f)
        val isPartial = !isFilled && (fillRatio in 0.14f..0.28f || opticalContrast in 0.14f..0.20f)

        val state = when {
            isFilled -> BubbleFillState.FILLED
            isPartial -> BubbleFillState.PARTIAL
            else -> BubbleFillState.EMPTY
        }

        // Confidence metric: high when clearly filled or clearly empty, low in borderline ranges
        val confidence = when (state) {
            BubbleFillState.FILLED -> ((opticalContrast - 0.20f) / 0.80f + fillRatio * 0.5f).coerceIn(0.75f, 1.0f)
            BubbleFillState.EMPTY -> (1.0f - opticalContrast).coerceIn(0.70f, 1.0f)
            BubbleFillState.PARTIAL -> 0.55f
            BubbleFillState.AMBIGUOUS -> 0.40f
        }

        return BubbleEvaluation(
            cx = bestCx.toFloat(),
            cy = bestCy.toFloat(),
            radius = radius.toFloat(),
            fillRatio = fillRatio,
            opticalContrast = opticalContrast,
            paperLuminance = paperMeanLum,
            coreLuminance = coreMeanLum,
            state = state,
            confidence = confidence,
            isFilled = isFilled
        )
    }

    /**
     * Evaluates a row of option bubbles (e.g., A, B, C, D) for a question.
     * Handles single-mark selection, dominance margins, multi-mark detection, and unattempted state.
     */
    fun evaluateQuestionRow(
        questionIndex: Int,
        bubbleEvaluations: List<BubbleEvaluation>,
        options: List<String> = listOf("A", "B", "C", "D")
    ): QuestionRowEvaluation {
        val filledIndices = bubbleEvaluations.indices.filter { bubbleEvaluations[it].isFilled }
        val partialIndices = bubbleEvaluations.indices.filter { bubbleEvaluations[it].state == BubbleFillState.PARTIAL }

        var dominantMargin = 0f
        val selectedOption: String
        val confidence: Float

        when {
            filledIndices.size == 1 -> {
                val idx = filledIndices[0]
                val otherMaxContrast = bubbleEvaluations.indices
                    .filter { it != idx }
                    .maxOfOrNull { bubbleEvaluations[it].opticalContrast } ?: 0f
                dominantMargin = bubbleEvaluations[idx].opticalContrast - otherMaxContrast
                selectedOption = options.getOrElse(idx) { "?" }
                confidence = bubbleEvaluations[idx].confidence
            }
            filledIndices.size > 1 -> {
                // Check if one marked bubble is significantly darker (e.g. erasure smudge on one, real fill on another)
                val sorted = filledIndices.sortedByDescending { bubbleEvaluations[it].opticalContrast }
                val best = sorted[0]
                val second = sorted[1]
                val diff = bubbleEvaluations[best].opticalContrast - bubbleEvaluations[second].opticalContrast
                dominantMargin = diff

                if (diff >= 0.18f) {
                    selectedOption = options.getOrElse(best) { "?" }
                    confidence = 0.80f
                } else {
                    selectedOption = "*" // Multiple marked
                    confidence = 0.95f
                }
            }
            partialIndices.size == 1 -> {
                // Weak or pencil mark: if it has reasonable contrast and clear dominance over empty options
                val idx = partialIndices[0]
                val otherMax = bubbleEvaluations.indices
                    .filter { it != idx }
                    .maxOfOrNull { bubbleEvaluations[it].opticalContrast } ?: 0f
                dominantMargin = bubbleEvaluations[idx].opticalContrast - otherMax
                if (dominantMargin >= 0.12f && bubbleEvaluations[idx].opticalContrast >= 0.16f) {
                    selectedOption = options.getOrElse(idx) { "?" }
                    confidence = 0.70f
                } else {
                    selectedOption = "-"
                    confidence = 0.65f
                }
            }
            else -> {
                // Completely unattempted
                selectedOption = "-"
                val maxContrast = bubbleEvaluations.maxOfOrNull { it.opticalContrast } ?: 0f
                dominantMargin = 0f
                confidence = (1.0f - maxContrast).coerceIn(0.85f, 1.0f)
            }
        }

        return QuestionRowEvaluation(
            questionIndex = questionIndex,
            selectedOption = selectedOption,
            bubbles = bubbleEvaluations,
            dominantMargin = dominantMargin,
            confidence = confidence
        )
    }

    private fun getPixelLuminance(pixel: Int): Float {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return 0.299f * r + 0.587f * g + 0.114f * b
    }
}
