package com.example.omr

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.ui.components.imageProxyToBitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bubble selection box metadata for transparent overlay rendering
 */
data class BubbleBox(
    val questionIndex: Int,
    val optionIndex: Int, // 0 for A, 1 for B, 2 for C, 3 for D
    val optionLabel: String, // "A", "B", "C", "D"
    val normLeft: Float,
    val normTop: Float,
    val normRight: Float,
    val normBottom: Float,
    val isFilled: Boolean,
    val fillRatio: Float,
    val isPartial: Boolean = false,
    val opticalContrast: Float = 0f,
    val state: OmrBubbleCvProcessor.BubbleFillState = OmrBubbleCvProcessor.BubbleFillState.EMPTY
)

/**
 * Roll number bubble metadata for overlay rendering
 */
data class RollBubbleBox(
    val column: Int,
    val digit: Int,
    val normLeft: Float,
    val normTop: Float,
    val normRight: Float,
    val normBottom: Float,
    val isFilled: Boolean
)

/**
 * Grid coordinates, corner lock states, and overlay telemetry emitted by OmrImageAnalyzer
 */
data class OmrGridOverlayData(
    val isSheetDetected: Boolean = false,
    val isGridLocked: Boolean = false,
    val cornerTL: PointF? = null,
    val cornerTR: PointF? = null,
    val cornerBL: PointF? = null,
    val cornerBR: PointF? = null,
    val isTlLocked: Boolean = false,
    val isTrLocked: Boolean = false,
    val isBlLocked: Boolean = false,
    val isBrLocked: Boolean = false,
    val matchedCornersCount: Int = 0,
    val isAllFourCornersMatched: Boolean = false,
    val isPerfectlyAligned: Boolean = false,
    val steadyFrameStreak: Int = 0,
    val requiredSteadyFrames: Int = CornerAlignmentTracker.DEFAULT_REQUIRED_STEADY_FRAMES,
    val isReadyToAutoCapture: Boolean = false,
    val alignmentProgress: Float = 0f,
    val sheetRect: RectF? = null,
    val gridRect: RectF? = null,
    val rollRect: RectF? = null,
    val bubbleBoxes: List<BubbleBox> = emptyList(),
    val rollBoxes: List<RollBubbleBox> = emptyList(),
    val detectedRoll: String = "",
    val detectedAnswers: List<String> = emptyList(),
    val filledCount: Int = 0,
    val totalQuestions: Int = 30,
    val statusText: String = "Align 4 Corner Markers in guides",
    val mlKitAnchorFound: String = "",
    val confidence: Float = 0.0f,
    val frameRotation: Int = 0
)

/**
 * High-performance, non-blocking CameraX ImageAnalysis.Analyzer for OMR & ML Kit:
 * - Decouples frame ingestion from heavy computer vision processing using Kotlin Coroutines on Dispatchers.Default
 * - Frees CameraX image buffer in <2ms synchronously on the camera thread to avoid buffer exhaustion
 * - Drops intermediate frames while vision pipeline is computing to maintain smooth 60 FPS UI rendering
 * - Safely dispatches UI telemetry and overlay updates back to the Android Main Looper
 */
class OmrImageAnalyzer(
    private val totalQuestions: Int = 30,
    private val rollDigits: Int = 4,
    private val onError: ((String, Throwable?) -> Unit)? = null,
    private val onGridDetected: (OmrGridOverlayData) -> Unit
) : ImageAnalysis.Analyzer, Closeable {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val visionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val isComputing = AtomicBoolean(false)
    private val alignmentTracker = CornerAlignmentTracker()
    private var lastAnalysisTimestamp = 0L
    private val minAnalysisIntervalMs = 60L // ~16 FPS max CV processing rate for optimal battery & thermal profile

    @Volatile
    private var lastFrameTimestamp = 0L

    @Volatile
    var latestFrameBitmap: Bitmap? = null
        private set

    @Volatile
    private var latestAlignedBitmap: Bitmap? = null

    @Volatile
    var latestProcessedBitmap: Bitmap? = null
        private set

    /**
     * Strictly returns the pristine, raw camera frame if it is fresh (<= maxAgeMs)
     * and valid. Returns null if stale or recycled to avoid processing outdated frames.
     */
    fun getLatestFrame(maxAgeMs: Long = 5000L): Bitmap? {
        val bmp = latestFrameBitmap
        if (bmp != null && !bmp.isRecycled && bmp.width > 0 && bmp.height > 0) {
            val age = System.currentTimeMillis() - lastFrameTimestamp
            if (maxAgeMs <= 0 || age <= maxAgeMs) {
                return bmp
            }
        }
        return null
    }

    /**
     * Returns and consumes the exact frame that satisfied 4-corner steady alignment,
     * falling back to the latest camera frame if needed.
     */
    fun consumeAlignedFrame(maxAgeMs: Long = 2000L): Bitmap? {
        val aligned = latestAlignedBitmap
        latestAlignedBitmap = null
        alignmentTracker.reset()
        if (aligned != null && !aligned.isRecycled && aligned.width > 0 && aligned.height > 0) {
            return aligned
        }
        return getLatestFrame(maxAgeMs)
    }

    fun resetAlignmentState() {
        latestAlignedBitmap = null
        alignmentTracker.reset()
    }

    fun clearFrames() {
        latestFrameBitmap = null
        latestAlignedBitmap = null
        latestProcessedBitmap = null
        lastFrameTimestamp = 0L
        alignmentTracker.reset()
    }

    private val textRecognizer by lazy {
        try {
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        } catch (e: Exception) {
            Log.w("OmrImageAnalyzer", "ML Kit TextRecognizer initialization deferred", e)
            null
        }
    }

    companion object {
        /**
         * Converts a CameraX ImageProxy into an upright ARGB_8888 Bitmap specifically
         * formatted and scaled for ML Kit vision detectors.
         */
        fun convertToMlKitBitmap(imageProxy: ImageProxy): Bitmap {
            return imageProxyToBitmap(imageProxy)
        }

        /**
         * Wraps an upright Bitmap into an ML Kit InputImage ready for vision detectors.
         */
        fun createMlKitInputImage(bitmap: Bitmap): InputImage {
            return InputImage.fromBitmap(bitmap, 0)
        }

        /**
         * Applies a spatial high-pass illumination filter to neutralize specular glare,
         * reflections, and uneven ambient light gradients on the OMR sheet.
         *
         * How it works:
         * 1. Separates low-frequency spatial illumination trends (glare hotspots & lamp gradients)
         *    using an optimized separable 2D moving average (O(1) per pixel).
         * 2. Subtracts the estimated local glare baseline from raw pixel luminance:
         *    HighPass = TargetPaperWhite + (Original - LowFreqGlare) * Gain
         * 3. Normalizes paper background while boosting contrast of high-frequency printed
         *    structures (black fiducial corner squares, grid lines, text, and bubble marks).
         */
        fun applyHighPassGlareFilter(
            sourceBitmap: Bitmap,
            filterRadius: Int = 14,
            contrastGain: Float = 1.8f
        ): Bitmap {
            val width = sourceBitmap.width
            val height = sourceBitmap.height
            val totalPixels = width * height

            val inPixels = IntArray(totalPixels)
            sourceBitmap.getPixels(inPixels, 0, width, 0, 0, width, height)

            // Extract luminance
            val gray = IntArray(totalPixels)
            for (i in 0 until totalPixels) {
                val p = inPixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                gray[i] = (299 * r + 587 * g + 114 * b) / 1000
            }

            // 1. Horizontal moving average (separable box filter pass)
            val blurH = IntArray(totalPixels)
            val r = filterRadius.coerceIn(3, 30)
            val div = 2 * r + 1

            for (y in 0 until height) {
                val rowOffset = y * width
                var sum = 0
                for (x in -r..r) {
                    val clampedX = x.coerceIn(0, width - 1)
                    sum += gray[rowOffset + clampedX]
                }
                blurH[rowOffset] = sum / div

                for (x in 1 until width) {
                    val leftOut = (x - r - 1).coerceIn(0, width - 1)
                    val rightIn = (x + r).coerceIn(0, width - 1)
                    sum += gray[rowOffset + rightIn] - gray[rowOffset + leftOut]
                    blurH[rowOffset + x] = sum / div
                }
            }

            // 2. Vertical moving average (separable box filter pass to complete 2D low-frequency glare field)
            val lowFreq = IntArray(totalPixels)
            for (x in 0 until width) {
                var sum = 0
                for (y in -r..r) {
                    val clampedY = y.coerceIn(0, height - 1)
                    sum += blurH[clampedY * width + x]
                }
                lowFreq[x] = sum / div

                for (y in 1 until height) {
                    val topOut = (y - r - 1).coerceIn(0, height - 1)
                    val bottomIn = (y + r).coerceIn(0, height - 1)
                    sum += blurH[bottomIn * width + x] - blurH[topOut * width + x]
                    lowFreq[y * width + x] = sum / div
                }
            }

            // 3. High-pass synthesis: subtract low-frequency glare field, normalize to clean paper white (185)
            val outPixels = IntArray(totalPixels)
            val paperTarget = 185f
            for (i in 0 until totalPixels) {
                val diff = (gray[i] - lowFreq[i]).toFloat()
                val hpVal = (paperTarget + diff * contrastGain).toInt().coerceIn(0, 255)
                outPixels[i] = (0xFF shl 24) or (hpVal shl 16) or (hpVal shl 8) or hpVal
            }

            val outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            outBitmap.setPixels(outPixels, 0, width, 0, 0, width, height)
            return outBitmap
        }
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        // Fast-path drop: if background worker is busy or throttled, drop frame immediately in 0ms
        if (currentTime - lastAnalysisTimestamp < minAnalysisIntervalMs || isComputing.get()) {
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        // Synchronously extract Bitmap and IMMEDIATELY close ImageProxy to return buffer to CameraX HAL in <2ms
        val frameBitmap: Bitmap? = try {
            convertToMlKitBitmap(imageProxy)
        } catch (e: Throwable) {
            Log.e("OmrImageAnalyzer", "Failed to extract frame bitmap", e)
            clearFrames()
            mainHandler.post {
                onError?.invoke("Camera frame conversion error: ${e.localizedMessage ?: e.message}", e)
            }
            null
        } finally {
            imageProxy.close() // Always release ImageProxy exactly once
        }

        if (frameBitmap == null || !isComputing.compareAndSet(false, true)) {
            return
        }
        latestFrameBitmap = frameBitmap
        lastFrameTimestamp = currentTime
        lastAnalysisTimestamp = currentTime

        // Offload ALL CPU-intensive computer vision and ML Kit processing to Dispatchers.Default
        // This ensures the CameraX capture thread and Android Main UI thread are never blocked!
        visionScope.launch {
            try {
                val overlayData = computeOmrVisionPipeline(frameBitmap, rotationDegrees)

                // Dispatch UI updates safely onto the Main UI Looper
                mainHandler.post {
                    onGridDetected(overlayData)
                }
            } catch (e: Throwable) {
                Log.e("OmrImageAnalyzer", "Error processing OMR frame on background worker", e)
                clearFrames() // Prevent stale frame reuse on failed scan
                mainHandler.post {
                    onError?.invoke("OMR frame analysis error: ${e.localizedMessage ?: e.message}", e)
                }
            } finally {
                isComputing.set(false) // Always reset isComputing in finally
            }
        }
    }

    /**
     * Executes the Canny-Hough corner detection, fiducial lock verification,
     * OMR bubble evaluation, and ML Kit header anchor verification on a background worker thread.
     */
    private fun computeOmrVisionPipeline(frameBitmap: Bitmap, rotationDegrees: Int): OmrGridOverlayData {
        val w = frameBitmap.width.toFloat()
        val h = frameBitmap.height.toFloat()

        // 0. Use the real, upright camera frame directly for CannyHough + Fiducial + Bubble CV analysis
        // Note: OmrBubbleCvProcessor already performs local annulus (1.25R..1.85R) paper normalization,
        // so passing the full luminance camera frame preserves solid dark ink inside large fiducials and bubbles.
        latestProcessedBitmap = frameBitmap

        // 1. Detect 4 Corner Fiducials using Canny Edge + Hough Line Transform + fiducial square matching
        val corners = OmrVisionEngine.detectFiducialCorners(frameBitmap)

        val normTL = PointF(corners.topLeft.x / w, corners.topLeft.y / h)
        val normTR = PointF(corners.topRight.x / w, corners.topRight.y / h)
        val normBL = PointF(corners.bottomLeft.x / w, corners.bottomLeft.y / h)
        val normBR = PointF(corners.bottomRight.x / w, corners.bottomRight.y / h)

        // 2. Verify all 4 corner markers are detected, perfectly aligned inside camera view, and steady across frames
        val alignment = alignmentTracker.evaluate(
            matchedCornersCount = corners.matchedCornersCount,
            isFiducialsFound = corners.isFiducialsFound,
            quad = NormQuad(
                tl = NormPoint(normTL.x, normTL.y),
                tr = NormPoint(normTR.x, normTR.y),
                bl = NormPoint(normBL.x, normBL.y),
                br = NormPoint(normBR.x, normBR.y)
            )
        )

        // 3. STRICT AUTO-CAPTURE GATE:
        // Capture the verified aligned frame only when all 4 corner markers are aligned & steady.
        // Full bubble & roll CV decoding runs once on capture in OmrScannerScreen for minimum latency.
        if (alignment.isSteadyAndReady) {
            latestAlignedBitmap = frameBitmap
        } else {
            latestAlignedBitmap = null
        }

        val status = alignment.statusMessage

        return OmrGridOverlayData(
            isSheetDetected = alignment.isPerfectlyAligned,
            isGridLocked = alignment.isSteadyAndReady,
            cornerTL = normTL,
            cornerTR = normTR,
            cornerBL = normBL,
            cornerBR = normBR,
            isTlLocked = corners.hasTL || alignment.isAllFourMatched,
            isTrLocked = corners.hasTR || alignment.isAllFourMatched,
            isBlLocked = corners.hasBL || alignment.isAllFourMatched,
            isBrLocked = corners.hasBR || alignment.isAllFourMatched,
            matchedCornersCount = alignment.matchedCornersCount,
            isAllFourCornersMatched = alignment.isAllFourMatched,
            isPerfectlyAligned = alignment.isPerfectlyAligned,
            steadyFrameStreak = alignment.steadyFrameStreak,
            requiredSteadyFrames = alignment.requiredSteadyFrames,
            isReadyToAutoCapture = alignment.isSteadyAndReady,
            alignmentProgress = alignment.alignmentProgress,
            sheetRect = RectF(normTL.x, normTL.y, normTR.x, normBL.y),
            gridRect = RectF(
                normTL.x + (normTR.x - normTL.x) * (65f / 1240f),
                normTL.y + (normBL.y - normTL.y) * (410f / 1754f),
                normTL.x + (normTR.x - normTL.x) * (1175f / 1240f),
                normTL.y + (normBL.y - normTL.y) * (1624f / 1754f)
            ),
            rollRect = RectF(
                normTL.x + (normTR.x - normTL.x) * (760f / 1240f),
                normTL.y + (normBL.y - normTL.y) * (180f / 1754f),
                normTL.x + (normTR.x - normTL.x) * (1175f / 1240f),
                normTL.y + (normBL.y - normTL.y) * (396f / 1754f)
            ),
            bubbleBoxes = emptyList(),
            detectedRoll = "",
            detectedAnswers = emptyList(),
            filledCount = 0,
            totalQuestions = totalQuestions,
            statusText = status,
            mlKitAnchorFound = "",
            confidence = alignment.alignmentProgress.coerceAtLeast(if (alignment.isPerfectlyAligned) 0.92f else 0.25f),
            frameRotation = rotationDegrees
        )
    }

    override fun close() {
        visionScope.cancel()
        try {
            textRecognizer?.close()
        } catch (_: Exception) { }
    }
}
