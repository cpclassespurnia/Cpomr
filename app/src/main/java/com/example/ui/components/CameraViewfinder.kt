package com.example.ui.components

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import java.io.ByteArrayOutputStream
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.omr.OmrGridOverlayData
import com.example.omr.OmrImageAnalyzer
import com.example.omr.OmrVisionEngine
import kotlinx.coroutines.delay
import java.nio.ByteBuffer
import java.util.concurrent.Executors

/**
 * Traverses context wrappers to find the host ComponentActivity.
 */
fun Context.findActivity(): ComponentActivity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return null
}

/**
 * Robust CameraX Viewfinder for OMR sheet scanning:
 * - Always opens the real physical camera via CameraX (CameraSelector.DEFAULT_BACK_CAMERA by default)
 * - Uses PreviewView.ImplementationMode.PERFORMANCE (SurfaceView) by default for zero-blackout hardware streaming
 * - Cleanly unbinds CameraProvider when disposed to prevent HAL lockouts
 * - Uses ResolutionSelector (1280x960 target) on ImageAnalysis for sharp 4-corner & bubble recognition
 * - Observes previewStreamState and CameraState and reports clear initialization/hardware errors
 */
@Composable
fun CameraViewfinder(
    modifier: Modifier = Modifier,
    imageCapture: ImageCapture? = null,
    torchEnabled: Boolean = false,
    clearOverlay: Boolean = false,
    showInternalOverlay: Boolean = false,
    showInternalHud: Boolean = true,
    totalQuestions: Int = 30,
    rollDigits: Int = 4,
    onGridOverlayUpdated: ((OmrGridOverlayData) -> Unit)? = null,
    onFrameAnalyzed: ((Bitmap) -> Unit)? = null,
    onPreviewViewReady: ((PreviewView) -> Unit)? = null,
    onAnalyzerReady: ((OmrImageAnalyzer) -> Unit)? = null,
    useSurfaceView: Boolean = true,
    telemetry: OmrVisionEngine.LiveScanTelemetry? = null,
    onCameraReady: () -> Unit = {},
    onCameraError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val composeLifecycleOwner = LocalLifecycleOwner.current
    val hostActivity = remember(context) { context.findActivity() }
    val lifecycleOwner: LifecycleOwner = hostActivity ?: composeLifecycleOwner

    var cameraInstance by remember { mutableStateOf<Camera?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var isStreaming by remember { mutableStateOf(false) }
    var useFrontCamera by remember { mutableStateOf(false) }
    var hasFrontCameraAvailable by remember { mutableStateOf(false) }
    var useTextureViewMode by remember(useSurfaceView) { mutableStateOf(!useSurfaceView) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    // Live overlay data from OMR & ML Kit ImageAnalysis.Analyzer
    var gridOverlayData by remember { mutableStateOf<OmrGridOverlayData?>(null) }

    LaunchedEffect(clearOverlay) {
        if (clearOverlay) {
            gridOverlayData = null
        }
    }

    // Dedicated single-thread executor for CameraX image analysis
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    // Instantiated analyzer with current question and roll parameters
    val omrImageAnalyzer = remember(totalQuestions, rollDigits) {
        OmrImageAnalyzer(
            totalQuestions = totalQuestions,
            rollDigits = rollDigits,
            onError = { errMsg, cause ->
                Log.w("CameraViewfinder", "Analyzer reported error: $errMsg", cause)
                onCameraError(errMsg)
            }
        ) { detectedOverlay ->
            gridOverlayData = detectedOverlay
            onGridOverlayUpdated?.invoke(detectedOverlay)
        }
    }

    DisposableEffect(omrImageAnalyzer) {
        onAnalyzerReady?.invoke(omrImageAnalyzer)
        onDispose {
            omrImageAnalyzer.close()
        }
    }

    // Crucial lifecycle unbinding: cleanly release Camera hardware when navigating away
    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                val providerFuture = ProcessCameraProvider.getInstance(context)
                if (providerFuture.isDone) {
                    providerFuture.get().unbindAll()
                    Log.i("CameraViewfinder", "ProcessCameraProvider unbindAll() invoked on dispose")
                }
            } catch (e: Exception) {
                Log.w("CameraViewfinder", "Error unbinding CameraProvider on dispose: ${e.message}")
            }
        }
    }

    // Control flash / torch
    LaunchedEffect(torchEnabled, cameraInstance) {
        try {
            cameraInstance?.cameraControl?.enableTorch(torchEnabled)
        } catch (_: Exception) { }
    }

    Box(modifier = modifier) {
        // Native CameraX PreviewView with SurfaceView (PERFORMANCE) or TextureView fallback
        key(retryTrigger, useFrontCamera, useTextureViewMode) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        // PERFORMANCE uses SurfaceView which is guaranteed to stream in Android Emulator and hardware
                        implementationMode = if (useTextureViewMode) {
                            PreviewView.ImplementationMode.COMPATIBLE
                        } else {
                            PreviewView.ImplementationMode.PERFORMANCE
                        }
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                    onPreviewViewReady?.invoke(previewView)
                    Log.i("CameraViewfinder", "PreviewView created. implementationMode=${previewView.implementationMode}")

                    // Monitor PreviewView StreamState
                    previewView.previewStreamState.observe(lifecycleOwner) { streamState ->
                        Log.i("CameraViewfinder", "PreviewView StreamState -> $streamState")
                        isStreaming = (streamState == PreviewView.StreamState.STREAMING)
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            if (lifecycleOwner.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.DESTROYED) {
                                Log.w("CameraViewfinder", "Lifecycle already DESTROYED; aborting CameraX bind")
                                return@addListener
                            }

                            val cameraProvider = cameraProviderFuture.get()
                            val availableCameras = cameraProvider.availableCameraInfos
                            Log.i("CameraViewfinder", "ProcessCameraProvider acquired. Found ${availableCameras.size} sensor(s)")

                            if (availableCameras.isEmpty()) {
                                val msg = "No camera hardware sensor detected on this device."
                                cameraError = msg
                                onCameraError(msg)
                                return@addListener
                            }

                            // Verify CameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) before binding
                            val hasBackCamera = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                            val hasFrontCamera = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                            hasFrontCameraAvailable = hasFrontCamera
                            Log.i("CameraViewfinder", "Diagnostic check: CameraProvider.hasCamera(DEFAULT_BACK_CAMERA) = $hasBackCamera, hasFrontCamera = $hasFrontCamera")

                            if (!useFrontCamera && !hasBackCamera) {
                                val msg = if (hasFrontCamera) {
                                    "No physical back camera detected (DEFAULT_BACK_CAMERA unavailable). Tap 'Use Front Camera' or use 'Demo OMR Sheet' / 'Import Photo'."
                                } else {
                                    "No rear camera detected on this device. Use 'Import Photo' or 'Demo OMR Sheet'."
                                }
                                cameraError = msg
                                onCameraError(msg)
                                return@addListener
                            }

                            val cameraSelector = if (useFrontCamera && hasFrontCamera) {
                                CameraSelector.DEFAULT_FRONT_CAMERA
                            } else {
                                CameraSelector.DEFAULT_BACK_CAMERA
                            }

                            cameraProvider.unbindAll()

                            // 1. Build Preview use case and attach SurfaceProvider
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            // 2. Build ImageAnalysis use case with native YUV_420_888 at high clarity resolution (1280x960 target)
                            val analysisResolutionSelector = ResolutionSelector.Builder()
                                .setResolutionStrategy(
                                    ResolutionStrategy(
                                        android.util.Size(1280, 960),
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                    )
                                )
                                .build()

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setResolutionSelector(analysisResolutionSelector)
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                                .build()
                                .also { analysis ->
                                    analysis.setAnalyzer(analysisExecutor, omrImageAnalyzer)
                                }

                            fun observeCameraHardwareState(cam: Camera) {
                                cam.cameraInfo.cameraState.observe(lifecycleOwner) { camState ->
                                    camState.error?.let { err ->
                                        val hwErr = "Camera hardware error (code ${err.code}): ${err.cause?.localizedMessage ?: "Camera unavailable"}"
                                        Log.e("CameraViewfinder", hwErr, err.cause)
                                        cameraError = hwErr
                                        onCameraError(hwErr)
                                    }
                                }
                            }

                            // 3. Bind CameraX use cases: Preview + ImageAnalysis + ImageCapture (if provided)
                            val useCases = mutableListOf<androidx.camera.core.UseCase>(preview, imageAnalysis)
                            if (imageCapture != null) {
                                useCases.add(imageCapture)
                            }

                            try {
                                val cam = cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    *useCases.toTypedArray()
                                )
                                cameraInstance = cam
                                observeCameraHardwareState(cam)
                                cameraError = null
                                Log.i("CameraViewfinder", "CameraX bound successfully to $cameraSelector (${useCases.size} use cases)")
                                onCameraReady()
                            } catch (e1: Exception) {
                                Log.w("CameraViewfinder", "Multi-usecase binding failed, binding Preview + ImageAnalysis", e1)
                                try {
                                    cameraProvider.unbindAll()
                                    val cam = cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        preview,
                                        imageAnalysis
                                    )
                                    cameraInstance = cam
                                    observeCameraHardwareState(cam)
                                    cameraError = null
                                    Log.i("CameraViewfinder", "CameraX bound successfully (Preview + ImageAnalysis)")
                                    onCameraReady()
                                } catch (e2: Exception) {
                                    Log.e("CameraViewfinder", "Preview + ImageAnalysis binding failed", e2)
                                    val errMsg = "Failed to bind CameraX Preview and ImageAnalysis: ${e2.localizedMessage ?: e2.message}"
                                    cameraError = errMsg
                                    onCameraError(errMsg)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("CameraViewfinder", "Fatal camera bind error", e)
                            val msg = e.localizedMessage ?: "Could not open camera"
                            cameraError = msg
                            onCameraError(msg)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                }
            )
        }

        // Optional Internal Overlay Layer: only rendered if requested internally (defaults to separate layer in parent)
        if (showInternalOverlay) {
            OmrOverlayCanvas(
                overlayData = if (clearOverlay) null else gridOverlayData,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Real-Time Corner Alignment & Detection Guidance HUD (only when enabled)
        if (showInternalHud) {
            val isLocked = gridOverlayData?.isGridLocked == true || gridOverlayData?.isSheetDetected == true
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (isLocked) Color(0xFF0F172A).copy(alpha = 0.92f) else Color(0xFF0F172A).copy(alpha = 0.85f),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isLocked) Color(0xFF10B981) else Color(0xFFF59E0B).copy(alpha = 0.6f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isLocked) Icons.Default.AutoAwesome else Icons.Default.Refresh,
                            contentDescription = null,
                            tint = if (isLocked) Color(0xFF10B981) else Color(0xFFF59E0B),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isLocked) {
                                gridOverlayData?.statusText ?: "4 CORNERS LOCKED • READY"
                            } else if (!isStreaming && cameraError == null) {
                                "STARTING CAMERA FEED..."
                            } else {
                                "ALIGN 4 CORNERS IN TARGET BRACKETS"
                            },
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Quick Flip Camera Lens Button
                IconButton(
                    onClick = { useFrontCamera = !useFrontCamera },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera (Front/Back)",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Camera Notice / Fallback Banner if camera hardware is unavailable
        if (cameraError != null) {
            Surface(
                color = Color(0xEE0F172A),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VideocamOff,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Camera Unavailable",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = cameraError ?: "No active camera stream",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                cameraError = null
                                retryTrigger++
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                        }
                        if (hasFrontCameraAvailable && !useFrontCamera) {
                            Button(
                                onClick = {
                                    useFrontCamera = true
                                    cameraError = null
                                    retryTrigger++
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                            ) {
                                Text("Front Camera", color = Color.White, fontSize = 12.sp)
                            }
                        } else {
                            Button(
                                onClick = {
                                    useTextureViewMode = !useTextureViewMode
                                    cameraError = null
                                    retryTrigger++
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                            ) {
                                Text(if (useTextureViewMode) "SurfaceView" else "TextureView", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val rotationDegrees = image.imageInfo.rotationDegrees

    val rawBitmap: Bitmap = when (image.format) {
        ImageFormat.JPEG -> {
            val plane = image.planes.getOrNull(0)
                ?: throw IllegalStateException("JPEG ImageProxy has no plane 0")
            val buffer = plane.buffer.duplicate()
            buffer.rewind()
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IllegalStateException("BitmapFactory failed to decode JPEG ImageProxy buffer (byteCount=${bytes.size})")
        }
        ImageFormat.YUV_420_888 -> {
            // CameraX provides image.toBitmap() natively for YUV_420_888
            var convertedBmp: Bitmap? = null
            try {
                convertedBmp = image.toBitmap()
            } catch (e: Exception) {
                Log.w("CameraViewfinder", "image.toBitmap() threw exception, falling back to manual YUV converter: ${e.message}")
            }
            convertedBmp ?: convertYuv420ToBitmap(image)
        }
        else -> {
            try {
                image.toBitmap()
            } catch (e: Exception) {
                throw UnsupportedOperationException("Unsupported ImageProxy format ${image.format} and image.toBitmap() failed", e)
            }
        }
    }

    if (rawBitmap.isRecycled || rawBitmap.width <= 0 || rawBitmap.height <= 0) {
        throw IllegalStateException("Decoded bitmap is invalid or recycled: ${rawBitmap.width}x${rawBitmap.height}")
    }

    return if (rotationDegrees != 0) {
        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
        }
        val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        if (rotated != rawBitmap && !rawBitmap.isRecycled) {
            rawBitmap.recycle()
        }
        rotated
    } else {
        rawBitmap
    }
}

/**
 * Proper YUV_420_888 to ARGB_8888 conversion routine:
 * - Respects Y, U, and V planes
 * - Respects each plane's rowStride and pixelStride
 * - Interleaves V and U bytes into NV21 format
 * - Compresses NV21 using android.graphics.YuvImage and decodes to Bitmap
 * - Throws a descriptive exception if conversion fails
 * - Never closes the ImageProxy (caller maintains ownership)
 */
private fun convertYuv420ToBitmap(image: ImageProxy): Bitmap {
    val width = image.width
    val height = image.height

    if (image.planes.size < 3) {
        throw IllegalStateException("YUV_420_888 ImageProxy does not contain 3 planes (found ${image.planes.size})")
    }

    val yPlane = image.planes[0]
    val uPlane = image.planes[1]
    val vPlane = image.planes[2]

    val yBuffer = yPlane.buffer.duplicate()
    val uBuffer = uPlane.buffer.duplicate()
    val vBuffer = vPlane.buffer.duplicate()
    yBuffer.rewind()
    uBuffer.rewind()
    vBuffer.rewind()

    val yRowStride = yPlane.rowStride
    val yPixelStride = yPlane.pixelStride
    val uRowStride = uPlane.rowStride
    val uPixelStride = uPlane.pixelStride
    val vRowStride = vPlane.rowStride
    val vPixelStride = vPlane.pixelStride

    // NV21 requires Y plane (width * height) followed by interleaved V and U (width * height / 2)
    val nv21 = ByteArray(width * height * 3 / 2)
    var nvIndex = 0

    // 1. Copy Y channel
    for (y in 0 until height) {
        val yRowStart = y * yRowStride
        for (x in 0 until width) {
            nv21[nvIndex++] = yBuffer.get(yRowStart + x * yPixelStride)
        }
    }

    // 2. Interleave V and U channels (NV21 format: V first, then U)
    val uvHeight = height / 2
    val uvWidth = width / 2
    for (y in 0 until uvHeight) {
        val uRowStart = y * uRowStride
        val vRowStart = y * vRowStride
        for (x in 0 until uvWidth) {
            val vByte = vBuffer.get(vRowStart + x * vPixelStride)
            val uByte = uBuffer.get(uRowStart + x * uPixelStride)
            nv21[nvIndex++] = vByte
            nv21[nvIndex++] = uByte
        }
    }

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val outStream = ByteArrayOutputStream()
    val ok = yuvImage.compressToJpeg(Rect(0, 0, width, height), 95, outStream)
    if (!ok) {
        throw IllegalStateException("YuvImage.compressToJpeg returned false for frame ${width}x${height}")
    }
    val jpegBytes = outStream.toByteArray()
    return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        ?: throw IllegalStateException("BitmapFactory.decodeByteArray failed on converted YUV->JPEG stream (size=${jpegBytes.size})")
}
