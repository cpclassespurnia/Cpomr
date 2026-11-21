package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.omr.OmrGridOverlayData

/**
 * Dedicated CanvasLayer:
 * Sits directly on top of the Camera Preview in the Box layout.
 * Exclusively handles lightweight vector alignment rendering:
 * 1. 4 clean square target alignment boxes at the A4 sheet corners
 * 2. Per-corner emerald highlight & lock indicator dots when each fiducial aligns
 * 3. Subtle aligned A4 perimeter guide when all 4 corners are geometry-aligned
 * 4. Center circular alignment progress / lock ring
 *
 * CRITICAL ARCHITECTURAL BOUNDARY:
 * - Operates strictly via hardware-accelerated Compose vector drawing.
 * - ZERO intermediate bitmaps, binary threshold masks, or opaque overlays
 *   are ever drawn directly onto the SurfaceView camera stream.
 */
@Composable
fun CanvasLayer(
    overlayData: OmrGridOverlayData?,
    modifier: Modifier = Modifier
) {
    OmrOverlayCanvas(overlayData = overlayData, modifier = modifier)
}

@Composable
fun OmrOverlayCanvas(
    overlayData: OmrGridOverlayData?,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "omr_ring_anim")
    val spinnerAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinner_angle"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasW = size.width
        val canvasH = size.height

        // Canonical A4 Aspect Ratio (1 : 1.4142) centered inside the viewfinder
        val targetRatio = 1f / 1.4142f
        var guideW = canvasW * 0.88f
        var guideH = guideW / targetRatio
        if (guideH > canvasH * 0.82f) {
            guideH = canvasH * 0.82f
            guideW = guideH * targetRatio
        }
        val gLeft = (canvasW - guideW) / 2f
        val gTop = (canvasH - guideH) / 2f
        val gRight = gLeft + guideW
        val gBottom = gTop + guideH

        val boxSize = 60.dp.toPx().coerceAtMost(guideW * 0.24f)
        val outerWhiteStroke = Stroke(width = 3.dp.toPx())
        val innerBlackStroke = Stroke(width = 1.6.dp.toPx())
        val innerLockedStroke = Stroke(width = 2.2.dp.toPx())

        val tlLocked = overlayData?.isTlLocked == true
        val trLocked = overlayData?.isTrLocked == true
        val blLocked = overlayData?.isBlLocked == true
        val brLocked = overlayData?.isBrLocked == true

        val isReady = overlayData?.isReadyToAutoCapture == true || overlayData?.isGridLocked == true
        val isAligned = overlayData?.isPerfectlyAligned == true
        val progress = (overlayData?.alignmentProgress ?: 0f).coerceIn(0f, 1f)

        val lockGreen = Color(0xFF16A34A)
        val lockBrightGreen = Color(0xFF22C55E)
        val ringBlue = Color(0xFF2563EB)

        // Subtle stroked A4 perimeter guide ONLY when all 4 corners are geometry-aligned
        if (isAligned || isReady) {
            drawRoundRect(
                color = (if (isReady) lockBrightGreen else lockGreen).copy(alpha = if (isReady) 0.90f else 0.65f * pulseAlpha),
                topLeft = Offset(gLeft, gTop),
                size = Size(guideW, guideH),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                style = Stroke(width = if (isReady) 2.5.dp.toPx() else 1.8.dp.toPx())
            )
        }

        val cornerBoxes = listOf(
            Offset(gLeft, gTop) to tlLocked,
            Offset(gRight - boxSize, gTop) to trLocked,
            Offset(gLeft, gBottom - boxSize) to blLocked,
            Offset(gRight - boxSize, gBottom - boxSize) to brLocked
        )

        cornerBoxes.forEach { (topLeft, isLocked) ->
            val rectSize = Size(boxSize, boxSize)

            // Subtle translucent emerald tint inside locked corner box
            if (isLocked) {
                drawRect(
                    color = lockBrightGreen.copy(alpha = 0.14f),
                    topLeft = topLeft,
                    size = rectSize
                )
            }

            // Contrast white outer stroke + crisp black/green inner stroke
            drawRect(
                color = Color.White.copy(alpha = 0.90f),
                topLeft = topLeft,
                size = rectSize,
                style = outerWhiteStroke
            )
            drawRect(
                color = if (isLocked) lockGreen else Color.Black.copy(alpha = 0.92f),
                topLeft = topLeft,
                size = rectSize,
                style = if (isLocked) innerLockedStroke else innerBlackStroke
            )

            // Green dot inside corner target box when that corner fiducial is locked
            if (isLocked) {
                val center = Offset(topLeft.x + boxSize / 2f, topLeft.y + boxSize / 2f)
                drawCircle(
                    color = Color.White,
                    radius = 7.dp.toPx(),
                    center = center
                )
                drawCircle(
                    color = lockBrightGreen,
                    radius = 5.dp.toPx(),
                    center = center
                )
            }
        }

        // Center circular lock / alignment progress ring
        val center = Offset(canvasW / 2f, canvasH / 2f)
        val ringRadius = 26.dp.toPx()
        val ringDiameter = ringRadius * 2f
        val ringTopLeft = Offset(center.x - ringRadius, center.y - ringRadius)
        val ringSize = Size(ringDiameter, ringDiameter)
        val ringStrokeWidth = 5.dp.toPx()

        if (isReady) {
            // Solid crisp white lock circle on auto-capture trigger with green confirmation border
            drawCircle(
                color = Color.White,
                radius = ringRadius + 2.dp.toPx(),
                center = center
            )
            drawCircle(
                color = lockBrightGreen,
                radius = ringRadius + 2.dp.toPx(),
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )
        } else if (isAligned && progress > 0f) {
            // White backing + emerald-blue closing progress arc while holding steady
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = ringRadius,
                center = center
            )
            drawArc(
                color = lockGreen.copy(alpha = 0.22f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = ringTopLeft,
                size = ringSize,
                style = Stroke(width = ringStrokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = lockGreen,
                startAngle = -90f,
                sweepAngle = (progress * 360f).coerceIn(45f, 360f),
                useCenter = false,
                topLeft = ringTopLeft,
                size = ringSize,
                style = Stroke(width = ringStrokeWidth, cap = StrokeCap.Round)
            )
        } else {
            // Rotating blue search ring
            drawArc(
                color = Color.White.copy(alpha = 0.28f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = ringTopLeft,
                size = ringSize,
                style = Stroke(width = ringStrokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = ringBlue.copy(alpha = pulseAlpha),
                startAngle = spinnerAngle,
                sweepAngle = 250f,
                useCenter = false,
                topLeft = ringTopLeft,
                size = ringSize,
                style = Stroke(width = ringStrokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}
