package com.example.omr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import kotlin.math.*

/**
 * High-performance computer vision module implementing:
 * 1. Grayscale & Gaussian smoothing for noise suppression under varying lighting
 * 2. Sobel edge gradient computation & Non-Maximum Suppression (Canny)
 * 3. Hysteresis edge thresholding
 * 4. Hough Line Transform with angle-quantized accumulator
 * 5. Closed-form line intersection to extract precise OMR sheet boundaries & corners
 */
object CannyHoughDetector {

    data class HoughLine(
        val r: Float,
        val thetaRad: Float,
        val votes: Int,
        val isHorizontal: Boolean
    )

    data class BoundaryCorners(
        val topLeft: PointF,
        val topRight: PointF,
        val bottomLeft: PointF,
        val bottomRight: PointF,
        val isConfident: Boolean,
        val detectedLinesCount: Int
    )

    // Precomputed Trigonometric Tables for 180 Hough angles
    private const val NUM_THETA = 180
    private val COS_TABLE = FloatArray(NUM_THETA) { thetaIdx ->
        cos(thetaIdx * Math.PI.toFloat() / 180f)
    }
    private val SIN_TABLE = FloatArray(NUM_THETA) { thetaIdx ->
        sin(thetaIdx * Math.PI.toFloat() / 180f)
    }

    /**
     * Detects the 4 outer boundary corners of an OMR sheet using Canny edge detection
     * followed by Hough Line Transformation.
     */
    fun detectSheetCorners(sourceBitmap: Bitmap): BoundaryCorners {
        val origW = sourceBitmap.width
        val origH = sourceBitmap.height

        // Downscale to working resolution for fast sub-20ms processing
        val targetWorkWidth = 380
        val scale = targetWorkWidth.toFloat() / origW.toFloat()
        val workW = targetWorkWidth
        val workH = (origH * scale).toInt().coerceAtLeast(100)

        val workingBitmap = if (origW > targetWorkWidth) {
            Bitmap.createScaledBitmap(sourceBitmap, workW, workH, true)
        } else {
            sourceBitmap
        }

        // 1. Grayscale extraction
        val gray = IntArray(workW * workH)
        val pixels = IntArray(workW * workH)
        workingBitmap.getPixels(pixels, 0, workW, 0, 0, workW, workH)

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            // Perceptual luminance
            gray[i] = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
        }

        // 2. Gaussian Blur (3x3 Kernel) to suppress camera sensor noise and uneven lighting
        val blurred = applyGaussianBlur3x3(gray, workW, workH)

        // 3. Sobel Edge Gradients (Gx, Gy) & Magnitude
        val gradMag = FloatArray(workW * workH)
        val gradDir = ByteArray(workW * workH) // Quantized to 0 (0°/180°), 1 (45°), 2 (90°), 3 (135°)
        computeSobelGradients(blurred, workW, workH, gradMag, gradDir)

        // 4. Non-Maximum Suppression (Edge Thinning)
        val nms = FloatArray(workW * workH)
        applyNonMaxSuppression(gradMag, gradDir, workW, workH, nms)

        // 5. Double Threshold & Hysteresis (Adaptive to image contrast)
        var maxGrad = 0f
        var totalGray = 0L
        for (v in nms) {
            if (v > maxGrad) maxGrad = v
        }
        for (g in gray) {
            totalGray += g
        }
        val avgGray = totalGray / gray.size
        if (avgGray < 55 || maxGrad < 30f) {
            // Insufficient illumination or contrast (dark frame or unopened camera buffer)
            val invScale = 1.0f / scale
            return BoundaryCorners(
                topLeft = PointF(workW * 0.05f * invScale, workH * 0.03f * invScale),
                topRight = PointF(workW * 0.95f * invScale, workH * 0.03f * invScale),
                bottomLeft = PointF(workW * 0.05f * invScale, workH * 0.97f * invScale),
                bottomRight = PointF(workW * 0.95f * invScale, workH * 0.97f * invScale),
                isConfident = false,
                detectedLinesCount = 0
            )
        }

        val highThreshold = (maxGrad * 0.28f).coerceIn(35f, 90f)
        val lowThreshold = highThreshold * 0.45f

        val edgeMap = BooleanArray(workW * workH)
        applyHysteresis(nms, workW, workH, lowThreshold, highThreshold, edgeMap)

        // 6. Hough Line Transform Accumulator
        val diag = hypot(workW.toFloat(), workH.toFloat())
        val maxR = ceil(diag).toInt()
        val rRange = 2 * maxR + 1
        val accum = IntArray(NUM_THETA * rRange)

        for (y in 2 until workH - 2) {
            val yOffset = y * workW
            for (x in 2 until workW - 2) {
                if (edgeMap[yOffset + x]) {
                    for (thetaIdx in 0 until NUM_THETA) {
                        val r = (x * COS_TABLE[thetaIdx] + y * SIN_TABLE[thetaIdx]).roundToInt()
                        val rIdx = r + maxR
                        accum[thetaIdx * rRange + rIdx]++
                    }
                }
            }
        }

        // 7. Find dominant boundary lines (Top, Bottom, Left, Right)
        val minVotes = (min(workW, workH) * 0.20f).toInt().coerceAtLeast(25)
        val boundaryLines = extractBoundaryLines(accum, NUM_THETA, rRange, maxR, workW, workH, minVotes)

        // 8. Intersect lines to compute 4 corners
        val cornersInWork = computeCornerIntersections(boundaryLines, workW, workH)

        // Scale coordinates back to original bitmap dimension
        val invScale = 1.0f / scale
        val tl = PointF(cornersInWork.topLeft.x * invScale, cornersInWork.topLeft.y * invScale)
        val tr = PointF(cornersInWork.topRight.x * invScale, cornersInWork.topRight.y * invScale)
        val bl = PointF(cornersInWork.bottomLeft.x * invScale, cornersInWork.bottomLeft.y * invScale)
        val br = PointF(cornersInWork.bottomRight.x * invScale, cornersInWork.bottomRight.y * invScale)

        return BoundaryCorners(
            topLeft = tl,
            topRight = tr,
            bottomLeft = bl,
            bottomRight = br,
            isConfident = cornersInWork.isConfident,
            detectedLinesCount = boundaryLines.size
        )
    }

    private fun applyGaussianBlur3x3(input: IntArray, w: Int, h: Int): IntArray {
        val output = IntArray(w * h)
        for (y in 1 until h - 1) {
            val yOffset = y * w
            for (x in 1 until w - 1) {
                val sum = (
                    input[yOffset - w + x - 1] + 2 * input[yOffset - w + x] + input[yOffset - w + x + 1] +
                    2 * input[yOffset + x - 1] + 4 * input[yOffset + x] + 2 * input[yOffset + x + 1] +
                    input[yOffset + w + x - 1] + 2 * input[yOffset + w + x] + input[yOffset + w + x + 1]
                )
                output[yOffset + x] = sum shr 4
            }
        }
        return output
    }

    private fun computeSobelGradients(
        gray: IntArray,
        w: Int,
        h: Int,
        magOut: FloatArray,
        dirOut: ByteArray
    ) {
        for (y in 1 until h - 1) {
            val yOffset = y * w
            for (x in 1 until w - 1) {
                val gx = (
                    -gray[yOffset - w + x - 1] + gray[yOffset - w + x + 1] -
                    2 * gray[yOffset + x - 1] + 2 * gray[yOffset + x + 1] -
                    gray[yOffset + w + x - 1] + gray[yOffset + w + x + 1]
                )

                val gy = (
                    -gray[yOffset - w + x - 1] - 2 * gray[yOffset - w + x] - gray[yOffset - w + x + 1] +
                    gray[yOffset + w + x - 1] + 2 * gray[yOffset + w + x] + gray[yOffset + w + x + 1]
                )

                val m = abs(gx) + abs(gy)
                val idx = yOffset + x
                magOut[idx] = m.toFloat()

                // Quantize angle
                val angle = atan2(gy.toFloat(), gx.toFloat()) * (180f / Math.PI.toFloat())
                val normAngle = (if (angle < 0) angle + 180f else angle) % 180f

                dirOut[idx] = when {
                    normAngle < 22.5f || normAngle >= 157.5f -> 0 // Horizontal (0°)
                    normAngle < 67.5f -> 1                       // Diagonal / (45°)
                    normAngle < 112.5f -> 2                      // Vertical (90°)
                    else -> 3                                    // Diagonal \ (135°)
                }
            }
        }
    }

    private fun applyNonMaxSuppression(
        mag: FloatArray,
        dir: ByteArray,
        w: Int,
        h: Int,
        nmsOut: FloatArray
    ) {
        for (y in 1 until h - 1) {
            val yOffset = y * w
            for (x in 1 until w - 1) {
                val idx = yOffset + x
                val m = mag[idx]
                if (m <= 0f) continue

                val isLocalMax = when (dir[idx].toInt()) {
                    0 -> m >= mag[idx - 1] && m >= mag[idx + 1]
                    1 -> m >= mag[idx - w + 1] && m >= mag[idx + w - 1]
                    2 -> m >= mag[idx - w] && m >= mag[idx + w]
                    3 -> m >= mag[idx - w - 1] && m >= mag[idx + w + 1]
                    else -> false
                }

                if (isLocalMax) {
                    nmsOut[idx] = m
                }
            }
        }
    }

    private fun applyHysteresis(
        nms: FloatArray,
        w: Int,
        h: Int,
        low: Float,
        high: Float,
        edgeOut: BooleanArray
    ) {
        for (y in 1 until h - 1) {
            val yOffset = y * w
            for (x in 1 until w - 1) {
                val idx = yOffset + x
                if (nms[idx] >= high && !edgeOut[idx]) {
                    edgeOut[idx] = true
                    // Propagate to weak edge neighbors
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val nIdx = idx + dy * w + dx
                            if (nms[nIdx] >= low) {
                                edgeOut[nIdx] = true
                            }
                        }
                    }
                }
            }
        }
    }

    private fun extractBoundaryLines(
        accum: IntArray,
        numThetas: Int,
        rRange: Int,
        maxR: Int,
        w: Int,
        h: Int,
        minVotes: Int
    ): List<HoughLine> {
        val detected = mutableListOf<HoughLine>()

        // Find local peaks in accumulator
        for (thetaIdx in 0 until numThetas) {
            val thetaRad = thetaIdx * Math.PI.toFloat() / 180f
            // Determine if line is roughly horizontal (normal theta near 90°) or vertical (normal theta near 0°/180°)
            val isHorizontal = thetaIdx in 65..115

            for (rIdx in 1 until rRange - 1) {
                val votes = accum[thetaIdx * rRange + rIdx]
                if (votes >= minVotes) {
                    // 1D non-max suppression across R
                    if (votes > accum[thetaIdx * rRange + rIdx - 1] && votes >= accum[thetaIdx * rRange + rIdx + 1]) {
                        val r = (rIdx - maxR).toFloat()
                        detected.add(HoughLine(r, thetaRad, votes, isHorizontal))
                    }
                }
            }
        }

        // Partition into Top, Bottom, Left, Right boundary lines
        var bestTop: HoughLine? = null
        var bestBottom: HoughLine? = null
        var bestLeft: HoughLine? = null
        var bestRight: HoughLine? = null

        val midY = h * 0.5f
        val midX = w * 0.5f

        for (line in detected.sortedByDescending { it.votes }) {
            if (line.isHorizontal) {
                // Intersect with x = midX to estimate y position
                val yAtMid = (line.r - midX * cos(line.thetaRad)) / sin(line.thetaRad)
                if (yAtMid in 0f..midY * 0.95f) {
                    if (bestTop == null || line.votes > bestTop.votes) {
                        bestTop = line
                    }
                } else if (yAtMid in (midY * 1.05f)..h.toFloat()) {
                    if (bestBottom == null || line.votes > bestBottom.votes) {
                        bestBottom = line
                    }
                }
            } else {
                // Vertical line: intersect with y = midY to estimate x position
                val cosVal = cos(line.thetaRad)
                if (abs(cosVal) > 0.1f) {
                    val xAtMid = (line.r - midY * sin(line.thetaRad)) / cosVal
                    if (xAtMid in 0f..midX * 0.95f) {
                        if (bestLeft == null || line.votes > bestLeft.votes) {
                            bestLeft = line
                        }
                    } else if (xAtMid in (midX * 1.05f)..w.toFloat()) {
                        if (bestRight == null || line.votes > bestRight.votes) {
                            bestRight = line
                        }
                    }
                }
            }
        }

        return listOfNotNull(bestTop, bestBottom, bestLeft, bestRight)
    }

    private fun computeCornerIntersections(
        lines: List<HoughLine>,
        w: Int,
        h: Int
    ): BoundaryCorners {
        val top = lines.firstOrNull { it.isHorizontal && it.r < h * 0.55f }
        val bottom = lines.firstOrNull { it.isHorizontal && it.r >= h * 0.45f }
        val left = lines.firstOrNull { !it.isHorizontal && it.r < w * 0.55f }
        val right = lines.firstOrNull { !it.isHorizontal && it.r >= w * 0.45f }

        val defaultTL = PointF(w * 0.05f, h * 0.03f)
        val defaultTR = PointF(w * 0.95f, h * 0.03f)
        val defaultBL = PointF(w * 0.05f, h * 0.97f)
        val defaultBR = PointF(w * 0.95f, h * 0.97f)

        val tl = if (top != null && left != null) intersectTwoLines(top, left) ?: defaultTL else defaultTL
        val tr = if (top != null && right != null) intersectTwoLines(top, right) ?: defaultTR else defaultTR
        val bl = if (bottom != null && left != null) intersectTwoLines(bottom, left) ?: defaultBL else defaultBL
        val br = if (bottom != null && right != null) intersectTwoLines(bottom, right) ?: defaultBR else defaultBR

        val isConfident = (top != null || bottom != null) && (left != null || right != null)

        return BoundaryCorners(
            topLeft = tl,
            topRight = tr,
            bottomLeft = bl,
            bottomRight = br,
            isConfident = isConfident,
            detectedLinesCount = lines.size
        )
    }

    private fun intersectTwoLines(line1: HoughLine, line2: HoughLine): PointF? {
        val c1 = cos(line1.thetaRad)
        val s1 = sin(line1.thetaRad)
        val c2 = cos(line2.thetaRad)
        val s2 = sin(line2.thetaRad)

        val det = c1 * s2 - s1 * c2
        if (abs(det) < 1e-4f) return null

        val x = (line1.r * s2 - line2.r * s1) / det
        val y = (line2.r * c1 - line1.r * c2) / det
        return PointF(x, y)
    }
}
