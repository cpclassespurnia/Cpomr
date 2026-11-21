package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.omr.OmrGridOverlayData
import com.example.omr.OmrImageAnalyzer
import com.example.omr.OmrVisionEngine
import com.example.ui.components.OmrOverlayCanvas
import com.example.ui.components.findActivity
import com.example.ui.theme.*
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainAppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * CameraScreen component using CameraX library to display a live camera preview,
 * allowing users to position OMR sheets for scanning.
 *
 * Features:
 * - Live CameraX PreviewView with lifecycle binding to host activity
 * - Real-time A4 sheet positioning guides with 4-corner alignment brackets
 * - Dynamic status HUD providing positioning tips (illumination, angle, corner alignment)
 * - Flashlight/torch toggle, camera lens switch (back/front), and photo picker import
 * - Auto-capture on 4-corner lock or instant manual capture
 * - Safe image buffer management to prevent ImageReader pool exhaustion
 */
@Composable
fun CameraScreen(
    modifier: Modifier = Modifier,
    viewModel: MainAppViewModel? = null,
    assessmentId: Long? = null,
    totalQuestions: Int = 30,
    rollDigits: Int = 4,
    onSheetCaptured: ((Bitmap) -> Unit)? = null,
    onClose: () -> Unit = {}
) {
    val context = LocalContext.current
    val composeLifecycleOwner = LocalLifecycleOwner.current
    val hostActivity = remember(context) { context.findActivity() }
    val targetLifecycleOwner: LifecycleOwner = hostActivity ?: composeLifecycleOwner
    val scope = rememberCoroutineScope()

    // Camera permission check
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Camera permission is required to scan OMR sheets", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler {
        onClose()
    }

    // Camera state
    var cameraInstance by remember { mutableStateOf<Camera?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var activePreviewView by remember { mutableStateOf<PreviewView?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    var useFrontCamera by remember { mutableStateOf(false) }
    var noBackCameraDetected by remember { mutableStateOf(false) }
    var diagnosticMessage by remember { mutableStateOf<String?>(null) }
    var hasFrontCameraAvailable by remember { mutableStateOf(false) }
    var autoScanEnabled by remember { mutableStateOf(true) }
    var isCapturing by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    // Live OMR detection telemetry
    var gridOverlayData by remember { mutableStateOf<OmrGridOverlayData?>(null) }
    var steadyLockFrames by remember { mutableIntStateOf(0) }

    // Analysis executor
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    // Instantiated analyzer
    val omrImageAnalyzer = remember(totalQuestions, rollDigits) {
        OmrImageAnalyzer(
            totalQuestions = totalQuestions,
            rollDigits = rollDigits
        ) { detectedOverlay ->
            gridOverlayData = detectedOverlay
        }
    }

    DisposableEffect(omrImageAnalyzer) {
        onDispose {
            omrImageAnalyzer.close()
        }
    }

    DisposableEffect(targetLifecycleOwner) {
        onDispose {
            try {
                val providerFuture = ProcessCameraProvider.getInstance(context)
                if (providerFuture.isDone) {
                    providerFuture.get().unbindAll()
                }
            } catch (_: Exception) {}
        }
    }

    // Torch control
    LaunchedEffect(torchEnabled, cameraInstance) {
        try {
            cameraInstance?.cameraControl?.enableTorch(torchEnabled)
        } catch (_: Exception) { }
    }

    // Photo picker fallback
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    onSheetCaptured?.invoke(bitmap)
                    if (viewModel != null && assessmentId != null) {
                        viewModel.navigateTo(AppScreen.OmrScanner(assessmentId))
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load selected photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Auto-capture when 4 corners are locked steadily for multiple frames
    LaunchedEffect(gridOverlayData?.isGridLocked) {
        if (gridOverlayData?.isGridLocked == true && autoScanEnabled && !isCapturing) {
            steadyLockFrames++
            if (steadyLockFrames >= 3) {
                steadyLockFrames = 0
                isCapturing = true
                val pv = activePreviewView
                val bmp = pv?.bitmap ?: omrImageAnalyzer.getLatestFrame()
                if (bmp != null) {
                    onSheetCaptured?.invoke(bmp)
                    if (viewModel != null && assessmentId != null) {
                        viewModel.navigateTo(AppScreen.OmrScanner(assessmentId))
                    }
                }
                delay(800)
                isCapturing = false
            }
        } else {
            steadyLockFrames = 0
        }
    }

    val isLocked = gridOverlayData?.isGridLocked == true || gridOverlayData?.isSheetDetected == true

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Black
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!hasCameraPermission) {
                // Permission Request Card
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Navy800),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Slate700),
                        modifier = Modifier.fillMaxWidth(0.92f)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Amber500,
                                modifier = Modifier.size(52.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Camera Access Required",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "To position and scan student OMR sheets in real time, please grant camera permission.",
                                color = Slate300,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = Amber500),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = Navy900)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Grant Permission", color = Navy900, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { photoPickerLauncher.launch("image/*") },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Blue500),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Blue500)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Import Photo from Gallery", fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else {
                // 1. CameraX Live Viewfinder
                key(retryTrigger, useFrontCamera) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                            activePreviewView = previewView

                            Log.i("CameraScreen", "PreviewView created. implementationMode=PERFORMANCE (SurfaceView), scaleType=FILL_CENTER")

                            // Monitor window attachment to verify layout hierarchy
                            previewView.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                                override fun onViewAttachedToWindow(v: android.view.View) {
                                    Log.i("CameraScreen", "PreviewView attached to window hierarchy: view size=${v.width}x${v.height}")
                                }
                                override fun onViewDetachedFromWindow(v: android.view.View) {
                                    Log.w("CameraScreen", "PreviewView detached from window hierarchy")
                                }
                            })

                            // Monitor PreviewView StreamState to verify the Preview surface is actively receiving frames
                            previewView.previewStreamState.observe(targetLifecycleOwner) { streamState ->
                                Log.i("CameraScreen", "PreviewView StreamState changed -> $streamState (isAttached=${previewView.isAttachedToWindow}, dimensions=${previewView.width}x${previewView.height})")
                                when (streamState) {
                                    PreviewView.StreamState.STREAMING -> {
                                        Log.i("CameraScreen", "SUCCESS: Camera Preview surface is correctly attached and actively streaming frames to PreviewView. UI overlay is transparent in center.")
                                    }
                                    PreviewView.StreamState.IDLE -> {
                                        Log.w("CameraScreen", "Camera Preview stream is IDLE (surface attached, waiting for camera hardware to start streaming)")
                                    }
                                    else -> {}
                                }
                            }

                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            Log.i("CameraScreen", "Requesting ProcessCameraProvider instance. Target LifecycleOwner: $targetLifecycleOwner (${targetLifecycleOwner.lifecycle.currentState})")

                            cameraProviderFuture.addListener({
                                try {
                                    val cameraProvider = cameraProviderFuture.get()
                                    val availableCameras = cameraProvider.availableCameraInfos
                                    Log.i("CameraScreen", "ProcessCameraProvider acquired successfully. Found ${availableCameras.size} available camera sensor(s).")
                                    availableCameras.forEachIndexed { idx, info ->
                                        Log.d("CameraScreen", "  Sensor #$idx: lensFacing=${info.lensFacing}, sensorRotation=${info.sensorRotationDegrees}")
                                    }

                                    // Diagnostic check: verify CameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) before attempting to bind to lifecycle
                                    val hasBackCamera = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                                    val hasFrontCamera = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                                    hasFrontCameraAvailable = hasFrontCamera
                                    Log.i("CameraScreen", "Diagnostic check: CameraProvider.hasCamera(DEFAULT_BACK_CAMERA) = $hasBackCamera, hasFrontCamera = $hasFrontCamera")

                                    if (!hasBackCamera) {
                                        val diagMsg = if (hasFrontCamera) {
                                            "No rear-facing (back) camera detected on this device. You can switch to the front camera or import an image from your gallery."
                                        } else {
                                            "No camera sensor detected on this device or emulator. Please import an OMR sheet photo from your gallery."
                                        }
                                        Log.w("CameraScreen", "Diagnostic check failed: $diagMsg")
                                        noBackCameraDetected = true
                                        diagnosticMessage = diagMsg

                                        // If user is currently targeting the back camera, do not attempt to bind to it
                                        if (!useFrontCamera) {
                                            cameraError = diagMsg
                                            return@addListener
                                        }
                                    } else {
                                        noBackCameraDetected = false
                                        diagnosticMessage = null
                                    }

                                    val preview = Preview.Builder().build().also {
                                        Log.i("CameraScreen", "Attaching Preview surface provider to PreviewView...")
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                        Log.i("CameraScreen", "Preview surfaceProvider attached successfully.")
                                    }

                                    // ImageAnalysis with native YUV_420_888 to avoid buffer starvation
                                    val imageAnalysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                                        .build()
                                        .also { analysis ->
                                            analysis.setAnalyzer(analysisExecutor, omrImageAnalyzer)
                                        }

                                    val cameraSelector = when {
                                        useFrontCamera && hasFrontCamera -> CameraSelector.DEFAULT_FRONT_CAMERA
                                        hasBackCamera -> CameraSelector.DEFAULT_BACK_CAMERA
                                        hasFrontCamera -> CameraSelector.DEFAULT_FRONT_CAMERA
                                        else -> availableCameras.firstOrNull()?.cameraSelector ?: CameraSelector.DEFAULT_BACK_CAMERA
                                    }

                                    cameraProvider.unbindAll()
                                    Log.i("CameraScreen", "Previous use cases unbound. Binding to lifecycle with selector: $cameraSelector")

                                    // Helper to monitor Camera hardware state
                                    fun attachCameraStateObserver(cam: Camera) {
                                        cam.cameraInfo.cameraState.observe(targetLifecycleOwner) { camState ->
                                            Log.i("CameraScreen", "CameraState transitioned to: ${camState.type} (error=${camState.error?.code})")
                                            camState.error?.let { err ->
                                                Log.e("CameraScreen", "Camera hardware error: code=${err.code}, reason=${err.cause?.message}", err.cause)
                                            }
                                        }
                                    }

                                    try {
                                        val cam = cameraProvider.bindToLifecycle(
                                            targetLifecycleOwner,
                                            cameraSelector,
                                            preview,
                                            imageAnalysis
                                        )
                                        cameraInstance = cam
                                        attachCameraStateObserver(cam)
                                        cameraError = null
                                        Log.i("CameraScreen", "Successfully bound CameraX with dual-stream (preview + imageAnalysis)")
                                    } catch (e1: Exception) {
                                        Log.w("CameraScreen", "Binding with imageAnalysis failed; fallback to preview only", e1)
                                        cameraProvider.unbindAll()
                                        val cam = cameraProvider.bindToLifecycle(
                                            targetLifecycleOwner,
                                            cameraSelector,
                                            preview
                                        )
                                        cameraInstance = cam
                                        attachCameraStateObserver(cam)
                                        cameraError = null
                                        Log.i("CameraScreen", "Successfully bound CameraX with preview-only")
                                    }
                                } catch (e: Exception) {
                                    Log.e("CameraScreen", "Fatal camera initialization error in ProcessCameraProvider listener", e)
                                    cameraError = e.localizedMessage ?: "Could not open camera"
                                }
                            }, ContextCompat.getMainExecutor(ctx))

                            previewView
                        }
                    )
                }

                // 2. Transparent OMR Sheet Positioning Guide & Corner Brackets Overlay
                OmrOverlayCanvas(
                    overlayData = gridOverlayData,
                    modifier = Modifier.fillMaxSize()
                )

                // 3. Top Header Bar: Close, Title & Action Toggles
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(42.dp)
                            .background(Navy900.copy(alpha = 0.75f), CircleShape)
                            .testTag("camera_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Surface(
                        color = Navy900.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(
                            1.dp,
                            when {
                                noBackCameraDetected && !useFrontCamera -> Amber500
                                isLocked -> Green500
                                else -> Amber500.copy(alpha = 0.6f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = when {
                                    noBackCameraDetected && !useFrontCamera -> Icons.Default.Warning
                                    isLocked -> Icons.Default.CheckCircle
                                    else -> Icons.Default.CropFree
                                },
                                contentDescription = null,
                                tint = when {
                                    noBackCameraDetected && !useFrontCamera -> Amber500
                                    isLocked -> Green500
                                    else -> Amber500
                                },
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = when {
                                    noBackCameraDetected && !useFrontCamera -> "NO BACK CAMERA DETECTED"
                                    isLocked -> "SHEET POSITION LOCKED"
                                    else -> "POSITION OMR SHEET"
                                },
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = { torchEnabled = !torchEnabled },
                            modifier = Modifier
                                .size(42.dp)
                                .background(if (torchEnabled) Amber500 else Navy900.copy(alpha = 0.75f), CircleShape)
                                .testTag("torch_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Torch",
                                tint = if (torchEnabled) Navy900 else Color.White
                            )
                        }

                        IconButton(
                            onClick = { showHelpDialog = true },
                            modifier = Modifier
                                .size(42.dp)
                                .background(Navy900.copy(alpha = 0.75f), CircleShape)
                                .testTag("camera_help_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = "Scanning Guide",
                                tint = Color.White
                            )
                        }
                    }
                }

                // 4. Positioning Instruction HUD (Top Center)
                Surface(
                    color = Navy900.copy(alpha = 0.90f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Slate700),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 76.dp, start = 20.dp, end = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CenterFocusStrong,
                            contentDescription = null,
                            tint = if (isLocked) Green500 else Amber500,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isLocked) {
                                gridOverlayData?.statusText ?: "4 Corners Locked • Ready to scan"
                            } else {
                                "Place sheet flat • Align 4 corner black boxes within brackets"
                            },
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // 5. Bottom Controls Bar
                Surface(
                    color = Navy900.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    border = BorderStroke(1.dp, Slate700),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Auto-Scan Toggle Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (autoScanEnabled) Green500 else Slate400,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Auto-Scan on Lock",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Switch(
                                checked = autoScanEnabled,
                                onCheckedChange = { autoScanEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Green500,
                                    checkedTrackColor = Green500.copy(alpha = 0.35f)
                                )
                            )
                        }

                        // Action Buttons: Import, Shutter, Switch Camera
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Import Photo Button
                            IconButton(
                                onClick = { photoPickerLauncher.launch("image/*") },
                                modifier = Modifier
                                    .size(50.dp)
                                    .background(Slate800, CircleShape)
                                    .testTag("import_photo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = "Import Photo",
                                    tint = Blue500,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Primary Shutter / Scan Button
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(if (isLocked) Green500.copy(alpha = 0.25f) else Amber500.copy(alpha = 0.20f))
                                    .clickable {
                                        if (!isCapturing) {
                                            isCapturing = true
                                            val pv = activePreviewView
                                            val bmp = pv?.bitmap ?: omrImageAnalyzer.getLatestFrame()
                                            if (bmp != null) {
                                                onSheetCaptured?.invoke(bmp)
                                                if (viewModel != null && assessmentId != null) {
                                                    viewModel.navigateTo(AppScreen.OmrScanner(assessmentId))
                                                }
                                            } else {
                                                Toast.makeText(context, "Align OMR sheet inside guides", Toast.LENGTH_SHORT).show()
                                            }
                                            scope.launch {
                                                delay(600)
                                                isCapturing = false
                                            }
                                        }
                                    }
                                    .testTag("shutter_capture_button")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(62.dp)
                                        .clip(CircleShape)
                                        .background(if (isLocked) Green500 else Amber500)
                                        .border(3.dp, Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Camera,
                                        contentDescription = "Capture Sheet",
                                        tint = Navy900,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }

                            // Switch Camera Lens Button
                            IconButton(
                                onClick = { useFrontCamera = !useFrontCamera },
                                modifier = Modifier
                                    .size(50.dp)
                                    .background(Slate800, CircleShape)
                                    .testTag("switch_camera_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cameraswitch,
                                    contentDescription = "Switch Camera",
                                    tint = Slate200,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                // 6. Camera Error & Diagnostic Fallback Banner
                if (noBackCameraDetected && !useFrontCamera) {
                    Surface(
                        color = Navy900.copy(alpha = 0.96f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, Amber500),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                            .fillMaxWidth(0.92f)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(Amber500.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideocamOff,
                                    contentDescription = null,
                                    tint = Amber500,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "No Back Camera Detected",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = diagnosticMessage
                                    ?: "Diagnostic check: CameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) returned false. No rear-facing camera sensor is available on this device or emulator.",
                                color = Slate300,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            if (hasFrontCameraAvailable) {
                                Button(
                                    onClick = {
                                        useFrontCamera = true
                                        cameraError = null
                                        retryTrigger++
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Amber500),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Cameraswitch, contentDescription = null, tint = Navy900)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Switch to Front Camera", color = Navy900, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                            OutlinedButton(
                                onClick = { photoPickerLauncher.launch("image/*") },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Blue500),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Blue500)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Import OMR Photo from Gallery", fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    cameraError = null
                                    retryTrigger++
                                }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Slate400, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Re-run Camera Diagnostics", color = Slate400, fontSize = 12.sp)
                            }
                        }
                    }
                } else if (cameraError != null) {
                    Surface(
                        color = Navy900.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Red500),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(28.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideocamOff,
                                contentDescription = null,
                                tint = Red500,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Camera Unavailable",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = cameraError ?: "Check camera permission or device sensor",
                                color = Slate400,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    cameraError = null
                                    retryTrigger++
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Amber500)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Navy900)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry Camera", color = Navy900, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Positioning Guidelines Dialog
        if (showHelpDialog) {
            AlertDialog(
                onDismissRequest = { showHelpDialog = false },
                containerColor = Navy800,
                titleContentColor = Color.White,
                textContentColor = Slate300,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Amber500,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text("How to Position OMR Sheet", fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("1. Place the printed A4 sheet flat on an even, well-lit surface.", fontSize = 13.sp)
                        Text("2. Hold your phone directly above the sheet so all 4 black corner squares fit inside the guide brackets.", fontSize = 13.sp)
                        Text("3. Avoid harsh glares or casting dark phone shadows over the bubble matrix.", fontSize = 13.sp)
                        Text("4. Once all 4 corners align, the brackets turn Emerald Green and the scanner automatically evaluates the sheet.", fontSize = 13.sp)
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { showHelpDialog = false }
                    ) {
                        Text("Got It", color = Amber500, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}
