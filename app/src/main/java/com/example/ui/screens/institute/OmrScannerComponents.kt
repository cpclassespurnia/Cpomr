package com.example.ui.screens.institute

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ScannedResultEntity
import com.example.data.model.StudentEntity
import com.example.ui.components.AppTopBar
import com.example.ui.theme.Amber500
import com.example.ui.theme.Blue100
import com.example.ui.theme.Blue500
import com.example.ui.theme.Green500
import com.example.ui.theme.Green600
import com.example.ui.theme.Navy800
import com.example.ui.theme.Navy900
import com.example.ui.theme.Red600
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate50
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900

data class CapturedReviewState(
    val roll: String,
    val setCode: String,
    val markedAnswers: List<String>,
    val previewBitmap: Bitmap,
    val attemptedCount: Int,
    val unattemptedCount: Int,
    val sourceName: String
)

@Composable
fun ScannerLightTopBar(
    title: String,
    subtitle: String,
    torchEnabled: Boolean,
    onBackClick: () -> Unit,
    onHelpClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onToggleTorch: () -> Unit,
    onImportPhoto: () -> Unit,
    onSwitchToDemo: () -> Unit
) {
    var showOverflowMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("scanner_light_top_bar"),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("scanner_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Slate900
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = title,
                    color = Slate900,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = Slate600,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onHelpClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("scanner_help_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                    contentDescription = "Scanning Guide",
                    tint = Slate700
                )
            }

            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("scanner_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Scanner Settings",
                    tint = Slate700
                )
            }

            Box {
                IconButton(
                    onClick = { showOverflowMenu = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("scanner_more_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = Slate700
                    )
                }

                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false }
                ) {
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = null,
                                tint = if (torchEnabled) Amber500 else Slate700
                            )
                        },
                        text = { Text(if (torchEnabled) "Flashlight Off" else "Flashlight On") },
                        onClick = {
                            showOverflowMenu = false
                            onToggleTorch()
                        }
                    )
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = Blue500
                            )
                        },
                        text = { Text("Import OMR Photo") },
                        onClick = {
                            showOverflowMenu = false
                            onImportPhoto()
                        },
                        modifier = Modifier.testTag("import_photo_button")
                    )
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.DocumentScanner,
                                contentDescription = null,
                                tint = Amber500
                            )
                        },
                        text = { Text("Demo OMR Sheet") },
                        onClick = {
                            showOverflowMenu = false
                            onSwitchToDemo()
                        },
                        modifier = Modifier.testTag("tab_demo_sheet")
                    )
                }
            }
        }
    }
}

@Composable
fun CapturedVerificationView(
    review: CapturedReviewState,
    hasAnswerKey: Boolean,
    isSaving: Boolean,
    onCancel: () -> Unit,
    onEdit: () -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .testTag("captured_verification_view")
    ) {
        // Light Summary Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Roll No ${review.roll}",
                    color = Slate900,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("captured_roll_title")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = Slate50,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Slate200)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckBox,
                                    contentDescription = null,
                                    tint = Blue500,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "${review.attemptedCount}",
                                    color = Slate900,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Attempted",
                                color = Slate600,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        color = Slate50,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Slate200)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckBoxOutlineBlank,
                                    contentDescription = null,
                                    tint = Slate400,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "${review.unattemptedCount}",
                                    color = Slate900,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Unattempted",
                                color = Slate600,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Text(
                    text = if (hasAnswerKey) {
                        "Set ${review.setCode} • Answer key ready"
                    } else {
                        "Answer key not set"
                    },
                    color = if (hasAnswerKey) Green600 else Slate600,
                    fontSize = 13.sp,
                    fontWeight = if (hasAnswerKey) FontWeight.Medium else FontWeight.Normal
                )
            }
        }

        // Captured Perspective-Warped Sheet Image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Slate100),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = review.previewBitmap.asImageBitmap(),
                contentDescription = "Captured OMR Sheet Verification Preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("captured_sheet_preview_image")
            )
        }

        // Bottom 3-Button Bar: Cancel | Edit | Save
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !isSaving,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Slate300),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate800),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("review_cancel_button")
                ) {
                    Text(
                        text = "Cancel",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                OutlinedButton(
                    onClick = onEdit,
                    enabled = !isSaving,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Slate300),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate800),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("review_edit_button")
                ) {
                    Text(
                        text = "Edit",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Button(
                    onClick = onSave,
                    enabled = !isSaving,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue500),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("review_save_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            text = "Save",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPermissionRequiredView(
    permissionDenied: Boolean,
    onRequestPermission: () -> Unit,
    onImportPhoto: () -> Unit,
    onSwitchToDemo: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate900)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Navy800),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Amber500.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Amber500.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = Amber500,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (permissionDenied) "Camera Permission Denied" else "Camera Permission Required",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (permissionDenied) {
                        "Camera access was denied. Grant CAMERA permission or open App Settings to scan printed OMR sheets."
                    } else {
                        "To scan student OMR sheets in real time, CP Classes needs permission to use your camera."
                    },
                    color = Slate300,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = Amber500),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = Navy900)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Grant Camera Permission", color = Navy900, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate300),
                        border = BorderStroke(1.dp, Slate600),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = Slate300, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Settings", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onImportPhoto,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Blue500),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Blue500, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import", fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onSwitchToDemo) {
                    Icon(Icons.Default.Quiz, contentDescription = null, tint = Amber500, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Switch to Demo OMR Sheet", color = Amber500, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun DemoSheetScannerView(
    demoSheetBitmap: Bitmap?,
    isProcessing: Boolean,
    onBackClick: () -> Unit,
    onSwitchToLiveCamera: () -> Unit,
    onImportPhoto: () -> Unit,
    onScanDemoSheet: () -> Unit
) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = "Demo OMR Scanner",
                subtitle = "OFFLINE TEST SHEET MODE",
                showBackButton = true,
                onBackClick = onBackClick
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Slate900)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .background(Navy800, RoundedCornerShape(10.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(7.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_live_camera")
                        .clickable { onSwitchToLiveCamera() }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Slate300, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Live Camera", color = Slate300, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    color = Amber500,
                    shape = RoundedCornerShape(7.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_demo_sheet")
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = Navy900, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Demo OMR Sheet", color = Navy900, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Slate900),
                contentAlignment = Alignment.Center
            ) {
                if (demoSheetBitmap != null) {
                    Image(
                        bitmap = demoSheetBitmap.asImageBitmap(),
                        contentDescription = "Demo OMR Sheet",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    CircularProgressIndicator(color = Amber500)
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp),
                    color = Navy900.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Green500)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Green500, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Demo Sheet Ready • Tap 'Scan & Grade' Below",
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (isProcessing) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Navy900.copy(alpha = 0.95f)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, Amber500)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = Amber500, strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text("Evaluating OMR Sheet...", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Navy900,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onScanDemoSheet,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("capture_omr_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Amber500),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = Navy900)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan & Grade OMR Sheet", color = Navy900, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onImportPhoto,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Slate600),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Blue500, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import Photo", fontSize = 12.sp, maxLines = 1)
                        }

                        Button(
                            onClick = onSwitchToLiveCamera,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Blue500),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Live Camera", fontSize = 12.sp, color = Color.White, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScannerSettingsDialog(
    selectedSet: String,
    autoScanEnabled: Boolean,
    selectedTargetRoll: String,
    students: List<StudentEntity>,
    onSetSelected: (String) -> Unit,
    onAutoScanChanged: (Boolean) -> Unit,
    onTargetRollSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Scanner Settings",
                fontWeight = FontWeight.Bold,
                color = Navy900,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Question Paper Set", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Slate700)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("A", "B", "C", "D").forEach { set ->
                            val isSelected = selectedSet == set
                            Surface(
                                color = if (isSelected) Blue500 else Slate100,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isSelected) Blue500 else Slate300),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clickable { onSetSelected(set) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Set $set",
                                        color = if (isSelected) Color.White else Slate800,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Slate200)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Capture", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Slate900)
                        Text("Snap automatically when all 4 corner markers align & hold steady", fontSize = 12.sp, color = Slate500)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = autoScanEnabled,
                        onCheckedChange = onAutoScanChanged
                    )
                }

                HorizontalDivider(color = Slate200)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Roll Number Mode", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Slate700)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item {
                            Surface(
                                color = if (selectedTargetRoll == "AUTO") Blue500.copy(alpha = 0.12f) else Slate50,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (selectedTargetRoll == "AUTO") Blue500 else Slate200),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onTargetRollSelected("AUTO") }
                            ) {
                                Text(
                                    text = "Auto-Detect from OMR Bubbles",
                                    color = if (selectedTargetRoll == "AUTO") Blue500 else Slate800,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                            }
                        }
                        items(students) { st ->
                            val isSelected = selectedTargetRoll == st.rollNumber
                            Surface(
                                color = if (isSelected) Blue500.copy(alpha = 0.12f) else Slate50,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isSelected) Blue500 else Slate200),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onTargetRollSelected(st.rollNumber) }
                            ) {
                                Text(
                                    text = "${st.name} (Roll #${st.rollNumber})",
                                    color = if (isSelected) Blue500 else Slate800,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Blue500)) {
                Text("Done", color = Color.White)
            }
        }
    )
}

@Composable
fun ScannerHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("How to Scan OMR Sheets", fontWeight = FontWeight.Bold, color = Navy900, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "1. Keep the 'Top' label aligned with the header of your CP Classes OMR sheet.",
                    color = Slate700,
                    fontSize = 13.sp
                )
                Text(
                    text = "2. Align all 4 black corner squares inside the 4 square target boxes. Each box turns green when locked.",
                    color = Slate700,
                    fontSize = 13.sp
                )
                Text(
                    text = "3. Hold steady — the center blue ring completes and auto-captures automatically (or tap the center circle manually).",
                    color = Slate700,
                    fontSize = 13.sp
                )
                Text(
                    text = "4. Verify detected Roll No & marked bubbles, then tap Save to log the sheet and scan the next student immediately.",
                    color = Slate700,
                    fontSize = 13.sp
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Blue500)) {
                Text("Got it", color = Color.White)
            }
        }
    )
}

@Composable
fun DuplicateScanDialog(
    dup: ScannedResultEntity,
    onViewExisting: () -> Unit,
    onOverwriteAndSave: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Amber500, modifier = Modifier.size(36.dp))
        },
        title = {
            Text("OMR Already Scanned!", fontWeight = FontWeight.Black, color = Navy900, fontSize = 18.sp)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Roll #${dup.studentRollNumber} has already been scanned for this exam.",
                    color = Slate700,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Slate100), shape = RoundedCornerShape(8.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Student: ${dup.studentName} (Roll #${dup.studentRollNumber})",
                            fontWeight = FontWeight.Bold,
                            color = Navy900,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Score: ${dup.score.toInt()} / ${dup.maxScore.toInt()} (${dup.percentage}%) • Rank #${dup.rank}",
                            color = Blue500,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Scanned: ${dup.scannedAtFormatted}",
                            color = Slate500,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onViewExisting, colors = ButtonDefaults.buttonColors(containerColor = Navy900)) {
                Text("View Scorecard")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onOverwriteAndSave) {
                    Text("Overwrite", color = Red600, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun ScanErrorAlertDialog(
    title: String,
    message: String,
    onDemoScan: () -> Unit,
    onRetryCamera: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Red600, modifier = Modifier.size(36.dp))
        },
        title = {
            Text(title, fontWeight = FontWeight.Black, color = Navy900, fontSize = 17.sp)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(message, color = Slate700, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Slate100), shape = RoundedCornerShape(8.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Tips for reliable scanning:", fontWeight = FontWeight.Bold, color = Navy900, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Align all 4 black corner squares inside the 4 target boxes.\n• Keep lighting even and avoid glare or shadows.\n• Or open 'Demo OMR Sheet' from the top-right menu for offline testing.",
                            color = Slate600,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDemoScan, colors = ButtonDefaults.buttonColors(containerColor = Green600)) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Demo Sheet")
            }
        },
        dismissButton = {
            Row {
                OutlinedButton(onClick = onRetryCamera) {
                    Text("Retry")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        }
    )
}
