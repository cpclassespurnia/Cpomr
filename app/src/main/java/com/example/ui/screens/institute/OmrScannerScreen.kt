package com.example.ui.screens.institute

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaActionSound
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.data.model.AssessmentEntity
import com.example.data.model.ScannedResultEntity
import com.example.omr.OmrGridOverlayData
import com.example.omr.OmrImageAnalyzer
import com.example.omr.OmrMlKitVisionHelper
import com.example.omr.OmrVisionEngine
import com.example.ui.components.CameraViewfinder
import com.example.ui.components.CanvasLayer
import com.example.ui.components.findActivity
import com.example.ui.components.imageProxyToBitmap
import com.example.ui.theme.Amber500
import com.example.ui.theme.Blue500
import com.example.ui.theme.Green500
import com.example.ui.theme.Navy900
import com.example.ui.theme.Red100
import com.example.ui.theme.Red500
import com.example.ui.theme.Red900
import com.example.ui.theme.Slate900
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainAppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class ScanSourceMode {
    CAMERA,
    DEMO_SHEET
}

@Composable
fun OmrScannerScreen(viewModel: MainAppViewModel, assessmentId: Long) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var assessment by remember { mutableStateOf<AssessmentEntity?>(null) }
    val students by viewModel.students.collectAsState()

    // Safe camera shutter click sound
    val shutterSound = remember {
        try {
            MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) }
        } catch (_: Exception) {
            null
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            try {
                shutterSound?.release()
            } catch (_: Exception) {
            }
        }
    }

    // Camera runtime permission handling
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        permissionDenied = !isGranted
        if (!isGranted) {
            Toast.makeText(
                context,
                "Camera permission denied. Grant CAMERA access to scan OMR sheets.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                hasCameraPermission = granted
                if (granted) {
                    permissionDenied = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var isProcessing by remember { mutableStateOf(false) }
    var isSavingGrade by remember { mutableStateOf(false) }
    var selectedSet by remember { mutableStateOf("A") }
    var torchEnabled by remember { mutableStateOf(false) }
    var autoScanEnabled by remember { mutableStateOf(true) }
    var cameraErrorMsg by remember { mutableStateOf<String?>(null) }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showSavedBanner by remember { mutableStateOf(false) }
    var showCaptureFlash by remember { mutableStateOf(false) }
    val captureFlashAlpha by animateFloatAsState(
        targetValue = if (showCaptureFlash) 0.55f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "capture_flash_alpha"
    )

    LaunchedEffect(showSavedBanner) {
        if (showSavedBanner) {
            delay(2400L)
            showSavedBanner = false
        }
    }

    LaunchedEffect(showCaptureFlash) {
        if (showCaptureFlash) {
            delay(160L)
            showCaptureFlash = false
        }
    }

    // Active PreviewView & analyzer references
    var activePreviewView by remember { mutableStateOf<PreviewView?>(null) }
    var activeAnalyzer by remember { mutableStateOf<OmrImageAnalyzer?>(null) }

    var selectedTargetRoll by remember { mutableStateOf("AUTO") }

    var liveTelemetry by remember { mutableStateOf<OmrVisionEngine.LiveScanTelemetry?>(null) }
    var currentGridOverlay by remember { mutableStateOf<OmrGridOverlayData?>(null) }
    var lastAutoCaptureAtMs by remember { mutableLongStateOf(0L) }

    // Verification preview state shown right after capture (before Save -> returns to camera)
    var capturedReview by remember { mutableStateOf<CapturedReviewState?>(null) }

    var activeScanMode by remember { mutableStateOf(ScanSourceMode.CAMERA) }
    var demoSheetBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(assessment, selectedSet, students) {
        withContext(Dispatchers.Default) {
            val totalQ = assessment?.totalQuestions ?: 30
            val rollDigits = assessment?.rollDigits ?: 4
            val unscannedStudent = students.firstOrNull { st ->
                viewModel.checkExistingResult(assessmentId, st.rollNumber) == null
            } ?: students.firstOrNull()
            val targetRoll = unscannedStudent?.rollNumber ?: "101"
            val options = listOf("A", "B", "C", "D")
            val sampleAnswers = (0 until totalQ).map { idx ->
                if (idx % 11 == 0) "-" else options[(idx * 2 + 1) % 4]
            }
            demoSheetBitmap = OmrVisionEngine.generateOmrSheetBitmap(
                testTitle = assessment?.title ?: "Periodic Test",
                rollNumber = targetRoll,
                studentName = unscannedStudent?.name ?: "Abhishek Kumar",
                batchName = assessment?.batchName ?: "Regular Batch",
                dateString = assessment?.dateString ?: "2026-10-24",
                totalQuestions = totalQ,
                rollDigits = rollDigits,
                markedAnswers = sampleAnswers,
                bubbledRoll = targetRoll,
                examId = assessmentId
            )
        }
    }

    LaunchedEffect(isProcessing, capturedReview) {
        if (isProcessing || capturedReview != null) {
            currentGridOverlay = null
        }
    }

    // Duplicate scan detection state (checked when user clicks Save on captured verification)
    var duplicateResult by remember { mutableStateOf<ScannedResultEntity?>(null) }
    var showDuplicateDialog by remember { mutableStateOf(false) }

    val imageCapture = remember { ImageCapture.Builder().build() }

    LaunchedEffect(assessmentId) {
        assessment = viewModel.getAssessmentById(assessmentId)
    }

    var scanErrorMessage by remember { mutableStateOf<String?>(null) }
    var failingStageInfo by remember { mutableStateOf<String?>(null) }

    fun isValidScanBitmap(bmp: Bitmap?): Boolean {
        if (bmp == null || bmp.isRecycled) return false
        if (bmp.width < 100 || bmp.height < 100) return false
        return true
    }

    fun playShutterFeedback() {
        try {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } catch (_: Exception) {
        }
        try {
            shutterSound?.play(MediaActionSound.SHUTTER_CLICK)
        } catch (_: Exception) {
        }
    }

    fun returnToLiveCamera(showSavedToast: Boolean = false) {
        capturedReview = null
        lastAutoCaptureAtMs = System.currentTimeMillis()
        activeAnalyzer?.resetAlignmentState()
        if (showSavedToast) {
            showSavedBanner = true
        }
    }

    fun saveCapturedReview(review: CapturedReviewState) {
        isSavingGrade = true
        viewModel.confirmAndSaveGrade(
            assessmentId = assessmentId,
            studentRoll = review.roll,
            setCode = review.setCode,
            markedAnswers = review.markedAnswers
        ) {
            isSavingGrade = false
            returnToLiveCamera(showSavedToast = true)
        }
    }

    fun handleCapturedBitmap(
        bitmap: Bitmap,
        overrideRoll: String? = null,
        sourceName: String = "Camera Preview"
    ) {
        if (!isValidScanBitmap(bitmap)) {
            isProcessing = false
            scanErrorMessage = "Captured image is invalid or blank ($sourceName). Ensure the OMR sheet is clearly visible."
            Log.e("OMR", "[OMR] handleCapturedBitmap aborted: invalid bitmap (${bitmap.width}x${bitmap.height})")
            return
        }

        isProcessing = true
        if (sourceName.startsWith("Auto-Capture") || sourceName.startsWith("CameraX")) {
            showCaptureFlash = true
        }
        scanErrorMessage = null
        failingStageInfo = null
        activeAnalyzer?.clearFrames()

        Log.i("OMR", "[OMR] 2. IMAGE_SOURCE_READY - source=$sourceName")
        Log.i("OMR", "[OMR] 3. IMAGE_CAPTURED - dimensions=${bitmap.width}x${bitmap.height}")

        scope.launch {
            try {
                val totalQ = assessment?.totalQuestions ?: 30
                val rollDigits = assessment?.rollDigits ?: 4
                val hasRoll = assessment?.includeRollField ?: true

                val (scanOutput, previewBmp) = withContext(Dispatchers.Default) {
                    val out = OmrVisionEngine.scanOmrBitmap(
                        bitmap = bitmap,
                        totalQuestions = totalQ,
                        optionsPerQuestion = 4,
                        rollDigits = rollDigits,
                        includeRollField = hasRoll
                    )
                    val annotatedPreview = OmrVisionEngine.buildVerificationPreviewBitmap(
                        bitmap = bitmap,
                        scanOutput = out,
                        rollDigits = rollDigits,
                        includeRollField = hasRoll
                    )
                    out to annotatedPreview
                }

                val mlKitMeta = try {
                    withTimeoutOrNull(1500L) {
                        OmrMlKitVisionHelper.analyzeFrameText(bitmap)
                    }
                } catch (e: Exception) {
                    Log.d("OMR", "ML Kit frame text analysis skipped: ${e.message}")
                    null
                }

                val finalRoll = when {
                    !overrideRoll.isNullOrBlank() -> overrideRoll
                    selectedTargetRoll != "AUTO" -> selectedTargetRoll
                    scanOutput.rollNumberDetected.isNotBlank() && scanOutput.rollNumberDetected != "101" -> scanOutput.rollNumberDetected
                    !mlKitMeta?.detectedRoll.isNullOrBlank() -> mlKitMeta!!.detectedRoll!!
                    scanOutput.rollNumberDetected.isNotBlank() -> scanOutput.rollNumberDetected
                    else -> students.firstOrNull()?.rollNumber ?: "101"
                }

                val effectiveSet = if (selectedSet == "A" && !mlKitMeta?.detectedSetCode.isNullOrBlank()) {
                    mlKitMeta!!.detectedSetCode!!
                } else {
                    selectedSet
                }

                val attempted = scanOutput.recognizedAnswers.count { it != "-" }
                val unattempted = (totalQ - attempted).coerceAtLeast(0)

                Log.i(
                    "OMR",
                    "[OMR] 10. CAPTURE_VERIFICATION_READY - assessmentId=$assessmentId, set=$effectiveSet, roll='$finalRoll', attempted=$attempted, unattempted=$unattempted"
                )

                playShutterFeedback()
                capturedReview = CapturedReviewState(
                    roll = finalRoll,
                    setCode = effectiveSet,
                    markedAnswers = scanOutput.recognizedAnswers,
                    previewBitmap = previewBmp,
                    attemptedCount = attempted,
                    unattemptedCount = unattempted,
                    sourceName = sourceName
                )
            } catch (e: OmrVisionEngine.OmrScanException) {
                Log.e("OMR", "[OMR] Pipeline halted at Stage ${e.stage} (${e.stageName}): ${e.message}", e)
                failingStageInfo = "Stage ${e.stage}: ${e.stageName}"
                scanErrorMessage = e.message
            } catch (e: Throwable) {
                Log.e("OMR", "[OMR] Unexpected error in scan pipeline: ${e.message}", e)
                failingStageInfo = "Pipeline Exception"
                scanErrorMessage = "Scanning failed: ${e.localizedMessage ?: e.message}"
            } finally {
                isProcessing = false
            }
        }
    }

    fun triggerCameraScan() {
        if (!hasCameraPermission) {
            permissionDenied = true
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }

        if (cameraErrorMsg != null) {
            isProcessing = false
            scanErrorMessage = "Camera initialization failed: $cameraErrorMsg"
            failingStageInfo = "Camera Unavailable"
            return
        }

        isProcessing = true
        scanErrorMessage = null
        failingStageInfo = null

        val isStreaming = activePreviewView?.previewStreamState?.value == PreviewView.StreamState.STREAMING

        val freshAnalyzerBmp = activeAnalyzer?.getLatestFrame(maxAgeMs = 3000L)
        if (isValidScanBitmap(freshAnalyzerBmp)) {
            handleCapturedBitmap(freshAnalyzerBmp!!, sourceName = "CameraX ImageAnalysis")
            return
        }

        val pvBmp = if (isStreaming) activePreviewView?.bitmap else null
        if (isValidScanBitmap(pvBmp)) {
            handleCapturedBitmap(pvBmp!!, sourceName = "CameraX Preview Stream")
            return
        }

        try {
            imageCapture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            val capturedBmp = imageProxyToBitmap(image)
                            if (isValidScanBitmap(capturedBmp)) {
                                handleCapturedBitmap(capturedBmp, sourceName = "CameraX ImageCapture")
                            } else {
                                isProcessing = false
                                scanErrorMessage = "Captured image was blank or corrupted. Hold steady over the OMR sheet."
                            }
                        } catch (e: Throwable) {
                            isProcessing = false
                            scanErrorMessage = "Image conversion error: ${e.localizedMessage ?: e.message}"
                        } finally {
                            image.close()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        isProcessing = false
                        scanErrorMessage = "Camera image capture error (${exception.imageCaptureError}): ${exception.message}."
                    }
                }
            )
        } catch (e: Exception) {
            isProcessing = false
            scanErrorMessage = "Camera initialization failed: ${e.localizedMessage ?: e.message}."
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            scanErrorMessage = null
            failingStageInfo = null
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    handleCapturedBitmap(bitmap, sourceName = "Gallery Photo")
                } else {
                    isProcessing = false
                    scanErrorMessage = "Failed to decode imported image file."
                }
            } catch (e: Exception) {
                isProcessing = false
                scanErrorMessage = "Error reading photo: ${e.localizedMessage ?: e.message}"
            }
        }
    }

    fun runSampleTestScan() {
        isProcessing = true
        scanErrorMessage = null
        failingStageInfo = null
        scope.launch {
            try {
                val totalQ = assessment?.totalQuestions ?: 30
                val rollDigits = assessment?.rollDigits ?: 4
                val unscannedStudent = students.firstOrNull { st ->
                    viewModel.checkExistingResult(assessmentId, st.rollNumber) == null
                } ?: students.firstOrNull()

                val targetRoll = unscannedStudent?.rollNumber ?: "101"
                val options = listOf("A", "B", "C", "D")
                val sampleAnswers = (0 until totalQ).map { idx ->
                    if (idx % 11 == 0) "-" else options[(idx * 2 + 1) % 4]
                }

                val sampleBmp = withContext(Dispatchers.Default) {
                    OmrVisionEngine.generateOmrSheetBitmap(
                        testTitle = assessment?.title ?: "Periodic Test",
                        rollNumber = targetRoll,
                        studentName = unscannedStudent?.name ?: "Student",
                        batchName = assessment?.batchName ?: "Regular Batch",
                        dateString = assessment?.dateString ?: "2026-10-24",
                        totalQuestions = totalQ,
                        rollDigits = rollDigits,
                        markedAnswers = sampleAnswers,
                        bubbledRoll = targetRoll,
                        examId = assessmentId
                    )
                }

                handleCapturedBitmap(sampleBmp, overrideRoll = null, sourceName = "Generated OMR Sheet")
            } catch (e: Exception) {
                isProcessing = false
                scanErrorMessage = "Demo sheet generation error: ${e.localizedMessage ?: e.message}"
            }
        }
    }

    // Light status bar icons for white scanner top bar and captured verification header
    val hostView = LocalView.current
    val isLightHeaderActive = (activeScanMode == ScanSourceMode.CAMERA) || (capturedReview != null)
    DisposableEffect(isLightHeaderActive, hostView) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, hostView) }
        val prevLightStatus = controller?.isAppearanceLightStatusBars ?: false
        if (controller != null && isLightHeaderActive) {
            controller.isAppearanceLightStatusBars = true
        }
        onDispose {
            if (controller != null && isLightHeaderActive) {
                controller.isAppearanceLightStatusBars = prevLightStatus
            }
        }
    }

    // Handle device back press from Captured Verification Preview smoothly back to Live Camera
    BackHandler(enabled = capturedReview != null) {
        returnToLiveCamera(showSavedToast = false)
    }

    if (capturedReview != null) {
        val review = capturedReview!!
        CapturedVerificationView(
            review = review,
            hasAnswerKey = assessment?.answerKeyConfigured == true,
            isSaving = isSavingGrade,
            onCancel = { returnToLiveCamera(showSavedToast = false) },
            onEdit = {
                capturedReview = null
                viewModel.navigateTo(
                    AppScreen.ScanVerification(
                        assessmentId = assessmentId,
                        roll = review.roll,
                        setCode = review.setCode,
                        markedAnswers = review.markedAnswers
                    )
                )
            },
            onSave = {
                scope.launch {
                    val existing = viewModel.checkExistingResult(assessmentId, review.roll)
                    if (existing != null) {
                        duplicateResult = existing
                        showDuplicateDialog = true
                    } else {
                        saveCapturedReview(review)
                    }
                }
            }
        )
    } else if (activeScanMode == ScanSourceMode.CAMERA) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .testTag("fullscreen_camera_scanner_root")
        ) {
            ScannerLightTopBar(
                title = assessment?.title ?: "OMR Scanner",
                subtitle = assessment?.batchName ?: "Live Camera",
                torchEnabled = torchEnabled,
                onBackClick = { viewModel.navigateBack() },
                onHelpClick = { showHelpDialog = true },
                onSettingsClick = { showSettingsDialog = true },
                onToggleTorch = { torchEnabled = !torchEnabled },
                onImportPhoto = { photoPickerLauncher.launch("image/*") },
                onSwitchToDemo = { activeScanMode = ScanSourceMode.DEMO_SHEET }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
                    .testTag("camera_preview_container_box")
            ) {
                if (!hasCameraPermission) {
                    CameraPermissionRequiredView(
                        permissionDenied = permissionDenied,
                        onRequestPermission = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                        onImportPhoto = { photoPickerLauncher.launch("image/*") },
                        onSwitchToDemo = { activeScanMode = ScanSourceMode.DEMO_SHEET }
                    )
                } else {
                    val totalQ = assessment?.totalQuestions ?: 30
                    val rollDigits = assessment?.rollDigits ?: 4

                    CameraViewfinder(
                        modifier = Modifier.fillMaxSize(),
                        imageCapture = imageCapture,
                        torchEnabled = torchEnabled,
                        clearOverlay = isProcessing,
                        showInternalOverlay = false,
                        showInternalHud = false,
                        useSurfaceView = true,
                        totalQuestions = totalQ,
                        rollDigits = rollDigits,
                        onGridOverlayUpdated = { overlay ->
                            currentGridOverlay = overlay
                            liveTelemetry = OmrVisionEngine.LiveScanTelemetry(
                                isSheetDetected = overlay.isPerfectlyAligned,
                                detectedRoll = overlay.detectedRoll,
                                filledCount = overlay.filledCount,
                                totalQuestions = overlay.totalQuestions,
                                confidence = overlay.confidence,
                                statusMessage = overlay.statusText,
                                isReadyToCapture = overlay.isReadyToAutoCapture
                            )

                            val nowMs = System.currentTimeMillis()
                            if (autoScanEnabled &&
                                overlay.isReadyToAutoCapture &&
                                !isProcessing &&
                                capturedReview == null &&
                                !showDuplicateDialog &&
                                scanErrorMessage == null &&
                                (nowMs - lastAutoCaptureAtMs > 2200L)
                            ) {
                                val candidate = activeAnalyzer?.consumeAlignedFrame(maxAgeMs = 1500L)
                                if (isValidScanBitmap(candidate)) {
                                    lastAutoCaptureAtMs = nowMs
                                    handleCapturedBitmap(
                                        candidate!!,
                                        sourceName = "Auto-Capture (4 Corners Aligned & Steady)"
                                    )
                                }
                            }
                        },
                        telemetry = liveTelemetry,
                        onPreviewViewReady = { pv -> activePreviewView = pv },
                        onAnalyzerReady = { analyzer -> activeAnalyzer = analyzer },
                        onCameraReady = { cameraErrorMsg = null },
                        onCameraError = { error -> cameraErrorMsg = error }
                    )

                    CanvasLayer(
                        overlayData = if (isProcessing) null else currentGridOverlay,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("detection_rectangles_canvas_layer")
                    )

                    // Subtle "Top" orientation pill matching the reference video
                    Surface(
                        color = Slate900.copy(alpha = 0.56f),
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 18.dp)
                    ) {
                        Text(
                            text = "Top",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                        )
                    }

                    // Center clickable shutter target right over the circular progress/lock ring
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(72.dp)
                            .clip(CircleShape)
                            .semantics { contentDescription = "Capture OMR Sheet" }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                enabled = !isProcessing,
                                onClick = { triggerCameraScan() }
                            )
                            .testTag("capture_omr_button")
                    )

                    if (cameraErrorMsg != null) {
                        Surface(
                            color = Red900.copy(alpha = 0.92f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Red500),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(horizontal = 16.dp, vertical = 56.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Red100, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Camera Error: $cameraErrorMsg",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Subtle shutter flash visual confirmation when auto-capture or manual snap triggers
                    if (captureFlashAlpha > 0.01f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White.copy(alpha = captureFlashAlpha))
                        )
                    }

                    // Compact non-intrusive alignment & auto-capture feedback pill at bottom
                    if (!isProcessing && !showSavedBanner) {
                        val overlay = currentGridOverlay
                        val isAligned = overlay?.isPerfectlyAligned == true
                        val isReady = overlay?.isReadyToAutoCapture == true
                        val statusDotColor = when {
                            !autoScanEnabled -> Blue500
                            isReady || isAligned -> Green500
                            (overlay?.matchedCornersCount ?: 0) > 0 -> Amber500
                            else -> Color.White.copy(alpha = 0.7f)
                        }
                        val statusLabel = when {
                            !autoScanEnabled -> "Manual • Tap center ring to scan"
                            isReady -> "Aligned • Capturing..."
                            isAligned -> "Aligned • Hold steady (${overlay?.steadyFrameStreak ?: 0}/${overlay?.requiredSteadyFrames ?: 5})"
                            overlay != null -> overlay.statusText
                            else -> "Align 4 black corners inside target boxes"
                        }

                        Surface(
                            color = Slate900.copy(alpha = 0.72f),
                            shape = CircleShape,
                            border = BorderStroke(
                                1.dp,
                                if (isAligned && autoScanEnabled) Green500.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.16f)
                            ),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 20.dp, vertical = 22.dp)
                                .heightIn(min = 48.dp)
                                .clip(CircleShape)
                                .clickable { autoScanEnabled = !autoScanEnabled }
                                .testTag("auto_capture_status_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (autoScanEnabled) Icons.Default.AutoMode else Icons.Default.TouchApp,
                                    contentDescription = if (autoScanEnabled) "Auto-Capture Enabled" else "Manual Capture Mode",
                                    tint = statusDotColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = statusLabel,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (isProcessing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(14.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = Blue500,
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "Reading OMR Sheet...",
                                        color = Navy900,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // Crisp "✓ Saved" feedback pill shown after saving in the live camera loop
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showSavedBanner,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 28.dp)
                    ) {
                        Surface(
                            color = Color.White,
                            shape = CircleShape,
                            shadowElevation = 6.dp,
                            modifier = Modifier.testTag("saved_toast_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircleOutline,
                                    contentDescription = null,
                                    tint = Blue500,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Saved",
                                    color = Slate900,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        DemoSheetScannerView(
            demoSheetBitmap = demoSheetBitmap,
            isProcessing = isProcessing,
            onBackClick = { viewModel.navigateBack() },
            onSwitchToLiveCamera = { activeScanMode = ScanSourceMode.CAMERA },
            onImportPhoto = { photoPickerLauncher.launch("image/*") },
            onScanDemoSheet = {
                if (demoSheetBitmap != null) {
                    handleCapturedBitmap(demoSheetBitmap!!, sourceName = "Demo OMR Sheet")
                } else {
                    runSampleTestScan()
                }
            }
        )
    }

    if (showSettingsDialog) {
        ScannerSettingsDialog(
            selectedSet = selectedSet,
            autoScanEnabled = autoScanEnabled,
            selectedTargetRoll = selectedTargetRoll,
            students = students,
            onSetSelected = { selectedSet = it },
            onAutoScanChanged = { autoScanEnabled = it },
            onTargetRollSelected = { selectedTargetRoll = it },
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showHelpDialog) {
        ScannerHelpDialog(onDismiss = { showHelpDialog = false })
    }

    if (showDuplicateDialog && duplicateResult != null) {
        val dup = duplicateResult!!
        DuplicateScanDialog(
            dup = dup,
            onViewExisting = {
                showDuplicateDialog = false
                duplicateResult = null
                capturedReview = null
                viewModel.navigateTo(AppScreen.ScoreReportDetail(dup.id, false))
            },
            onOverwriteAndSave = {
                showDuplicateDialog = false
                duplicateResult = null
                val pending = capturedReview
                if (pending != null) {
                    saveCapturedReview(pending)
                }
            },
            onCancel = {
                showDuplicateDialog = false
                duplicateResult = null
            }
        )
    }

    if (scanErrorMessage != null) {
        ScanErrorAlertDialog(
            title = failingStageInfo ?: "OMR Scan Failed",
            message = scanErrorMessage ?: "An error occurred during sheet evaluation.",
            onDemoScan = {
                scanErrorMessage = null
                failingStageInfo = null
                runSampleTestScan()
            },
            onRetryCamera = {
                scanErrorMessage = null
                failingStageInfo = null
                triggerCameraScan()
            },
            onDismiss = {
                scanErrorMessage = null
                failingStageInfo = null
                lastAutoCaptureAtMs = System.currentTimeMillis()
                activeAnalyzer?.resetAlignmentState()
            }
        )
    }
}
