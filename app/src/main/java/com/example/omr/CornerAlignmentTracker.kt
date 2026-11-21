package com.example.omr

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

data class NormPoint(
    val x: Float,
    val y: Float
)

data class NormQuad(
    val tl: NormPoint,
    val tr: NormPoint,
    val bl: NormPoint,
    val br: NormPoint
)

data class CornerAlignmentResult(
    val matchedCornersCount: Int,
    val isAllFourMatched: Boolean,
    val isWithinCameraView: Boolean,
    val isGeometryAligned: Boolean,
    val isPerfectlyAligned: Boolean,
    val steadyFrameStreak: Int,
    val requiredSteadyFrames: Int,
    val isSteadyAndReady: Boolean,
    val alignmentProgress: Float,
    val statusMessage: String
)

/**
 * Evaluates whether all 4 OMR sheet corner markers are simultaneously:
 * 1. Detected (all 4 black fiducial markers matched, not extrapolated)
 * 2. Contained cleanly inside the camera viewport without edge clipping
 * 3. Geometrically aligned (level orientation, symmetric opposite edges & diagonals, valid span & centering)
 * 4. Temporally steady across [requiredSteadyFrames] consecutive frames
 *
 * Used to gate OMR bubble detection and trigger auto-capture only on crisp, steady, aligned sheets.
 */
class CornerAlignmentTracker(
    val requiredSteadyFrames: Int = DEFAULT_REQUIRED_STEADY_FRAMES,
    private val maxCornerJitter: Float = DEFAULT_MAX_CORNER_JITTER
) {

    private var previousAlignedQuad: NormQuad? = null
    private var currentSteadyStreak: Int = 0

    @Synchronized
    fun reset() {
        previousAlignedQuad = null
        currentSteadyStreak = 0
    }

    @Synchronized
    fun evaluate(
        matchedCornersCount: Int,
        isFiducialsFound: Boolean,
        quad: NormQuad
    ): CornerAlignmentResult {
        val isAllFourMatched = isFiducialsFound && matchedCornersCount >= 4

        val points = listOf(quad.tl, quad.tr, quad.bl, quad.br)
        val isWithinCameraView = points.all { pt ->
            pt.x in MIN_EDGE_MARGIN..MAX_EDGE_MARGIN &&
                pt.y in MIN_EDGE_MARGIN..MAX_EDGE_MARGIN
        }

        val hasProperOrdering = quad.tl.x < quad.tr.x &&
            quad.bl.x < quad.br.x &&
            quad.tl.y < quad.bl.y &&
            quad.tr.y < quad.br.y

        val topW = hypot(quad.tr.x - quad.tl.x, quad.tr.y - quad.tl.y)
        val botW = hypot(quad.br.x - quad.bl.x, quad.br.y - quad.bl.y)
        val leftH = hypot(quad.bl.x - quad.tl.x, quad.bl.y - quad.tl.y)
        val rightH = hypot(quad.br.x - quad.tr.x, quad.br.y - quad.tr.y)

        val avgW = (topW + botW) * 0.5f
        val avgH = (leftH + rightH) * 0.5f
        val hasValidSpan = avgW >= MIN_WIDTH_FRACTION && avgH >= MIN_HEIGHT_FRACTION

        val widthAsymmetry = abs(topW - botW) / maxOf(topW, botW, 1e-4f)
        val heightAsymmetry = abs(leftH - rightH) / maxOf(leftH, rightH, 1e-4f)

        val diag1 = hypot(quad.br.x - quad.tl.x, quad.br.y - quad.tl.y)
        val diag2 = hypot(quad.bl.x - quad.tr.x, quad.bl.y - quad.tr.y)
        val diagAsymmetry = abs(diag1 - diag2) / maxOf(diag1, diag2, 1e-4f)

        val isBalancedRect = widthAsymmetry <= MAX_EDGE_ASYMMETRY &&
            heightAsymmetry <= MAX_EDGE_ASYMMETRY &&
            diagAsymmetry <= MAX_EDGE_ASYMMETRY

        val topTilt = abs(atan2(quad.tr.y - quad.tl.y, quad.tr.x - quad.tl.x))
        val botTilt = abs(atan2(quad.br.y - quad.bl.y, quad.br.x - quad.bl.x))
        val leftTilt = abs(atan2(quad.bl.x - quad.tl.x, quad.bl.y - quad.tl.y))
        val rightTilt = abs(atan2(quad.br.x - quad.tr.x, quad.br.y - quad.tr.y))
        val isLevel = topTilt <= MAX_TILT_RADIANS &&
            botTilt <= MAX_TILT_RADIANS &&
            leftTilt <= MAX_TILT_RADIANS &&
            rightTilt <= MAX_TILT_RADIANS

        val centerX = (quad.tl.x + quad.tr.x + quad.bl.x + quad.br.x) * 0.25f
        val centerY = (quad.tl.y + quad.tr.y + quad.bl.y + quad.br.y) * 0.25f
        val isCentered = centerX in MIN_CENTER..MAX_CENTER &&
            centerY in MIN_CENTER..MAX_CENTER

        val isGeometryAligned = hasProperOrdering &&
            hasValidSpan &&
            isBalancedRect &&
            isLevel &&
            isCentered

        val isPerfectlyAligned = isAllFourMatched && isWithinCameraView && isGeometryAligned

        if (isPerfectlyAligned) {
            val prev = previousAlignedQuad
            if (prev != null) {
                val maxDelta = maxOf(
                    hypot(quad.tl.x - prev.tl.x, quad.tl.y - prev.tl.y),
                    hypot(quad.tr.x - prev.tr.x, quad.tr.y - prev.tr.y),
                    hypot(quad.bl.x - prev.bl.x, quad.bl.y - prev.bl.y),
                    hypot(quad.br.x - prev.br.x, quad.br.y - prev.br.y)
                )
                currentSteadyStreak = if (maxDelta <= maxCornerJitter) {
                    (currentSteadyStreak + 1).coerceAtMost(requiredSteadyFrames)
                } else {
                    1
                }
            } else {
                currentSteadyStreak = 1
            }
            previousAlignedQuad = quad
        } else {
            currentSteadyStreak = 0
            previousAlignedQuad = null
        }

        val isSteadyAndReady = isPerfectlyAligned && currentSteadyStreak >= requiredSteadyFrames

        val progress = when {
            isSteadyAndReady -> 1.0f
            isPerfectlyAligned -> 0.40f + 0.60f * (currentSteadyStreak.toFloat() / requiredSteadyFrames.toFloat())
            isAllFourMatched && isWithinCameraView -> 0.35f
            else -> (matchedCornersCount.coerceIn(0, 4) / 4f) * 0.25f
        }.coerceIn(0f, 1f)

        val statusMessage = when {
            isSteadyAndReady -> "4 corners locked & steady • Auto-capturing..."
            isPerfectlyAligned -> "4 corners aligned • Hold steady ($currentSteadyStreak/$requiredSteadyFrames)"
            !isAllFourMatched -> "Detected $matchedCornersCount/4 corners — fit all 4 black squares"
            !isWithinCameraView -> "Move back slightly — keep all 4 corners inside view"
            !hasValidSpan -> "Move closer so the OMR sheet fills the frame"
            !isCentered -> "Center the OMR sheet inside the guide frame"
            !isLevel || !isBalancedRect -> "Hold camera flat & level over the sheet"
            else -> "Align 4 corner markers inside guides"
        }

        return CornerAlignmentResult(
            matchedCornersCount = matchedCornersCount,
            isAllFourMatched = isAllFourMatched,
            isWithinCameraView = isWithinCameraView,
            isGeometryAligned = isGeometryAligned,
            isPerfectlyAligned = isPerfectlyAligned,
            steadyFrameStreak = currentSteadyStreak,
            requiredSteadyFrames = requiredSteadyFrames,
            isSteadyAndReady = isSteadyAndReady,
            alignmentProgress = progress,
            statusMessage = statusMessage
        )
    }

    companion object {
        const val DEFAULT_REQUIRED_STEADY_FRAMES = 5
        const val DEFAULT_MAX_CORNER_JITTER = 0.020f

        private const val MIN_EDGE_MARGIN = 0.02f
        private const val MAX_EDGE_MARGIN = 0.98f
        private const val MIN_WIDTH_FRACTION = 0.30f
        private const val MIN_HEIGHT_FRACTION = 0.35f
        private const val MAX_EDGE_ASYMMETRY = 0.15f
        private const val MAX_TILT_RADIANS = 0.18f // ~10 degrees max handheld tilt
        private const val MIN_CENTER = 0.22f
        private const val MAX_CENTER = 0.78f
    }
}
