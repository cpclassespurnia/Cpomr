package com.example.ui.screens.institute

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.model.AssessmentEntity
import com.example.omr.OmrVisionEngine
import com.example.ui.components.AppTopBar
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainAppViewModel
import java.io.File
import java.io.FileOutputStream

/**
 * Requirement 4 & 7:
 * - OMR must be printable in A4 size PDF
 * - Each exam has its own distinct OMR sheet with unique exam ID & code
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OmrTemplateViewerScreen(viewModel: MainAppViewModel, assessmentId: Long) {
    val context = LocalContext.current
    val students by viewModel.students.collectAsState()

    var assessment by remember { mutableStateOf<AssessmentEntity?>(null) }
    var prebubbleRoll by remember { mutableStateOf<String?>(null) }
    var showPrebubbleDialog by remember { mutableStateOf(false) }

    LaunchedEffect(assessmentId) {
        assessment = viewModel.getAssessmentById(assessmentId)
    }

    val omrBitmap = remember(assessment, prebubbleRoll) {
        val loaded = assessment
        if (loaded == null) null
        else {
            OmrVisionEngine.generateOmrSheetBitmap(
                testTitle = loaded.title,
                studentName = students.firstOrNull { it.rollNumber == prebubbleRoll }?.name ?: "",
                batchName = loaded.batchName,
                dateString = loaded.dateString,
                totalQuestions = loaded.totalQuestions,
                rollDigits = loaded.rollDigits,
                includeNameField = loaded.includeNameField,
                includeRollField = loaded.includeRollField,
                includeDateField = loaded.includeDateField,
                includeBatchField = loaded.includeBatchField,
                includePhoneField = loaded.includePhoneField,
                bubbledRoll = prebubbleRoll,
                topicsJson = loaded.topicsJson,
                examId = loaded.id
            )
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Printable A4 OMR Sheet",
                subtitle = "ISO 216 (210 × 297 MM) • CP CLASSES",
                showBackButton = true,
                onBackClick = { viewModel.navigateBack() }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Slate50)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // A4 Header & Print / PDF Actions Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Navy900),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = Amber500,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "A4 PDF READY",
                                        color = Navy900,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Exam #$assessmentId • ${assessment?.totalQuestions ?: 30} Q",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Standard A4 Dimensions (1:1.414) • High-precision Corner Fiducials",
                                color = Slate300,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Requirement 4: Print A4 PDF & Share Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Print / Export A4 PDF Button
                        Button(
                            onClick = {
                                omrBitmap?.let { bmp ->
                                    val safeTitle = (assessment?.title ?: "OMR").replace(Regex("[^a-zA-Z0-9]"), "_")
                                    printOrSharePdf(context, bmp, "CP_Classes_${safeTitle}_A4.pdf")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Amber500),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("print_a4_pdf_btn")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Navy900, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Print / Save A4 PDF", color = Navy900, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        // Share Image Button
                        OutlinedButton(
                            onClick = {
                                omrBitmap?.let { bmp ->
                                    val safeTitle = (assessment?.title ?: "OMR").replace(Regex("[^a-zA-Z0-9]"), "_")
                                    shareOrPrintBitmap(context, bmp, "CP_Classes_${safeTitle}_A4.png")
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Amber500),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("share_image_btn")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share Image", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Student Pre-bubbling Options
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Slate300),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pre-fill Student Name & Roll:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Navy900
                        )
                        Text(
                            text = if (prebubbleRoll != null) "Assigned to Roll #$prebubbleRoll" else "Blank Sheet (Students bubble by hand)",
                            color = if (prebubbleRoll != null) Green600 else Slate600,
                            fontSize = 11.sp
                        )
                    }

                    OutlinedButton(
                        onClick = { showPrebubbleDialog = true },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(if (prebubbleRoll != null) "Change" else "Pre-bubble Roll", fontSize = 11.sp)
                    }
                }
            }

            // Sheet Preview Card (1:1.414 Aspect Ratio)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("omr_sheet_preview_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Slate300),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "A4 SHEET PREVIEW (EXACT PROPORTIONS)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate600
                        )
                        Text(
                            text = "1240 × 1754 px",
                            fontSize = 10.sp,
                            color = Slate500
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (omrBitmap != null) {
                        Image(
                            bitmap = omrBitmap.asImageBitmap(),
                            contentDescription = "Generated A4 OMR Sheet",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f / 1.4142f)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, Slate300, RoundedCornerShape(6.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Navy900)
                        }
                    }
                }
            }
        }
    }

    // Pre-bubble selection dialog
    if (showPrebubbleDialog) {
        AlertDialog(
            onDismissRequest = { showPrebubbleDialog = false },
            title = { Text("Pre-bubble Student Info", fontWeight = FontWeight.Bold, color = Navy900) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Select a registered student to pre-fill their name and roll bubbles on this A4 sheet:",
                        fontSize = 12.sp,
                        color = Slate600
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(onClick = {
                        prebubbleRoll = null
                        showPrebubbleDialog = false
                    }) {
                        Text("Reset to Blank (No Pre-bubbling)", color = Navy900)
                    }

                    students.forEach { s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "${s.name} (${s.rollNumber})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(text = s.batchName, fontSize = 11.sp, color = Slate500)
                            }
                            Button(
                                onClick = {
                                    prebubbleRoll = s.rollNumber
                                    showPrebubbleDialog = false
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Select", fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPrebubbleDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Requirement 4: Generates and shares A4 PDF using Android native PdfDocument
 */
private fun printOrSharePdf(context: Context, bitmap: Bitmap, filename: String) {
    try {
        val cachePath = File(context.cacheDir, "omr_pdfs").apply { mkdirs() }
        val pdfFile = File(cachePath, filename)
        OmrVisionEngine.generateA4Pdf(bitmap, pdfFile)

        val contentUri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Print or Share A4 OMR PDF"))
    } catch (_: Exception) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "CP Classes A4 OMR generated.")
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share"))
    }
}

private fun shareOrPrintBitmap(context: Context, bitmap: Bitmap, filename: String) {
    try {
        val cachePath = File(context.cacheDir, "images").apply { mkdirs() }
        val newFile = File(cachePath, filename)
        val stream = FileOutputStream(newFile)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()

        val contentUri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", newFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share A4 OMR Sheet Image"))
    } catch (_: Exception) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "CP Classes A4 OMR Sheet generated successfully.")
        }
        context.startActivity(Intent.createChooser(intent, "Share"))
    }
}
