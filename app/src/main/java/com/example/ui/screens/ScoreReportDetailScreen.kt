package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.data.model.QuestionEvaluation
import com.example.data.model.QuestionStatus
import com.example.data.model.ScannedResultEntity
import com.example.data.model.StudentEntity
import com.example.omr.OmrVisionEngine
import com.example.ui.components.AppTopBar
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainAppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun ScoreReportDetailScreen(
    viewModel: MainAppViewModel,
    resultId: Long,
    fromStudentView: Boolean = false
) {
    val context = LocalContext.current
    val results by viewModel.scannedResults.collectAsState()
    val students by viewModel.students.collectAsState()

    val result = results.firstOrNull { it.id == resultId }
    val student = students.firstOrNull { it.rollNumber == result?.studentRollNumber }

    var evaluations by remember { mutableStateOf<List<QuestionEvaluation>>(emptyList()) }
    var annotatedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGeneratingBitmap by remember { mutableStateOf(false) }

    LaunchedEffect(result) {
        val r = result
        if (r != null) {
            evaluations = viewModel.getDetailedEvaluations(r)
        }
    }

    LaunchedEffect(result, evaluations) {
        val r = result
        if (r != null && evaluations.isNotEmpty()) {
            isGeneratingBitmap = true
            withContext(Dispatchers.Default) {
                try {
                    val bmp = OmrVisionEngine.generateAnnotatedGradedOmrBitmap(
                        testTitle = r.assessmentTitle,
                        studentRoll = r.studentRollNumber,
                        studentName = r.studentName,
                        batchName = r.batchName,
                        dateString = r.scannedAtFormatted,
                        totalQuestions = evaluations.size,
                        rollDigits = r.studentRollNumber.length.coerceIn(1, 10),
                        evaluations = evaluations,
                        examId = r.assessmentId,
                        score = r.score,
                        maxScore = r.maxScore,
                        rank = r.rank
                    )
                    annotatedBitmap = bmp
                } catch (_: Exception) { }
            }
            isGeneratingBitmap = false
        }
    }

    val topicResults = remember(result) {
        viewModel.getTopicResults(result?.topicResultsJson ?: "")
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Official Scorecard & Report",
                subtitle = "CP CLASSES PURNIA",
                showBackButton = true,
                onBackClick = { viewModel.navigateBack() }
            )
        }
    ) { paddingValues ->
        val res = result
        if (res == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Navy900)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Slate50)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Main Scorecard Banner Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scorecard_banner_card"),
                        colors = CardDefaults.cardColors(containerColor = Navy900),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            // Student Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = res.studentName,
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Roll: ${res.studentRollNumber} • ${res.batchName}",
                                        color = Amber500,
                                        fontSize = 13.sp
                                    )
                                }

                                Surface(
                                    color = Amber500,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "RANK #${res.rank}",
                                        color = Navy900,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Divider(color = Navy700)
                            Spacer(modifier = Modifier.height(14.dp))

                            // Overall Marks & Percentile
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column {
                                    Text(text = "TOTAL SCORE", color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = "${res.score.toInt()}",
                                            color = Color.White,
                                            fontSize = 34.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                        Text(
                                            text = " / ${res.maxScore.toInt()}",
                                            color = Slate400,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(text = "PERCENTILE", color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "${res.percentile}%",
                                        color = Green500,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Topper Comparison Banner
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Navy800,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Blue500.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Amber500, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Topper Marks: ${res.topperScore.toInt()} / ${res.maxScore.toInt()}",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (res.topperName.isNotBlank()) {
                                        Text(
                                            text = "(${res.topperName})",
                                            color = Amber500,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Accuracy & Question counts
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                StatChip(label = "Accuracy", value = "${res.accuracy}%", color = Blue500)
                                StatChip(label = "Correct", value = "${res.correctCount}", color = Green500)
                                StatChip(label = "Wrong", value = "${res.wrongCount}", color = Red500)
                                StatChip(label = "Unattempted", value = "${res.unattemptedCount}", color = Slate400)
                            }
                        }
                    }
                }

                // Requirement 6 & 4: Stored Evaluated Color-Coded OMR Sheet
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("annotated_omr_sheet_card"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Slate300)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "COLOR-CODED EVALUATED OMR SHEET",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate600,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = "Standard ISO 216 A4 Record",
                                        fontSize = 11.sp,
                                        color = Slate500
                                    )
                                }

                                Surface(
                                    color = Green50,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Verified Audit",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Green600,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Color Legend (Requirement 6)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Slate100)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF16A34A)))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Correct", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF16A34A))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFDC2626)))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Wrong", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFDC2626))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFEAB308)))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Non-Attempt", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFB45309))
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Image display
                            if (annotatedBitmap != null) {
                                Image(
                                    bitmap = annotatedBitmap!!.asImageBitmap(),
                                    contentDescription = "Evaluated OMR Sheet with color markings",
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
                                        .height(240.dp)
                                        .background(Slate100, RoundedCornerShape(6.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = Navy900)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Action buttons for A4 PDF download / print (Requirement 4)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        annotatedBitmap?.let { bmp ->
                                            val safeName = "${res.studentRollNumber}_${res.studentName}".replace(Regex("[^a-zA-Z0-9]"), "_")
                                            shareOrPrintA4Pdf(context, bmp, "CP_Classes_OMR_${safeName}.pdf")
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .testTag("download_a4_pdf_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = Navy900),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Amber500, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Print / Save A4 PDF", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        annotatedBitmap?.let { bmp ->
                                            val safeName = "${res.studentRollNumber}_${res.studentName}".replace(Regex("[^a-zA-Z0-9]"), "_")
                                            shareAnnotatedImage(context, bmp, "CP_Classes_OMR_${safeName}.png")
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(0.8f)
                                        .height(46.dp)
                                        .testTag("share_omr_image_button"),
                                    border = BorderStroke(1.dp, Navy900),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = Navy900, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Share OMR", color = Navy900, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Topic-Wise Results Breakdown
                if (topicResults.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("topic_wise_results_card"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Slate300)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "TOPIC WISE PERFORMANCE BREAKDOWN",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate600,
                                        letterSpacing = 1.sp
                                    )
                                    Surface(
                                        color = Blue50,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "${topicResults.size} Topics",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Blue500,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                topicResults.forEach { t ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(containerColor = Slate50),
                                        border = BorderStroke(0.8.dp, Slate300)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(
                                                        text = t.topicName,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Navy900
                                                    )
                                                    if (t.optionalLimit != null) {
                                                        Text(
                                                            text = "Optional limit: max ${t.optionalLimit} evaluated (${t.attempted} attempted)",
                                                            fontSize = 11.sp,
                                                            color = Amber600,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                }

                                                Text(
                                                    text = "${t.score.toInt()} / ${t.maxScore.toInt()}",
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Navy900
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(text = "Correct: ${t.correct}", fontSize = 11.sp, color = Green600, fontWeight = FontWeight.SemiBold)
                                                Text(text = "Wrong: ${t.wrong}", fontSize = 11.sp, color = Red600, fontWeight = FontWeight.SemiBold)
                                                Text(text = "Unattempted: ${t.unattempted}", fontSize = 11.sp, color = Slate600)
                                                Text(text = "Accuracy: ${t.accuracyPercent}%", fontSize = 11.sp, color = Blue500, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // WhatsApp & Email Share Action Buttons
                item {
                    val studentEmail = student?.email ?: "student@cpclasses.in"
                    val studentPhone = student?.whatsapp ?: "+91 98351 24567"
                    val reportSummaryText = buildReportShareText(res, student)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                sendViaWhatsApp(context, studentPhone, reportSummaryText)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("share_whatsapp_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send WhatsApp", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                sendViaEmail(context, studentEmail, "Scorecard: ${res.assessmentTitle} - ${res.studentName}", reportSummaryText)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("share_email_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Navy900),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, tint = Amber500)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send Email", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Question by Question Evaluation Table Header
                item {
                    Text(
                        text = "QUESTION BY QUESTION EVALUATION (${evaluations.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate600,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(evaluations) { eval ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(0.8.dp, Slate300),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (eval.status) {
                                                QuestionStatus.CORRECT -> Green100
                                                QuestionStatus.WRONG -> Red100
                                                QuestionStatus.EXCLUDED_OPTIONAL -> Amber100
                                                else -> Slate100
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${eval.questionNumber}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (eval.status) {
                                            QuestionStatus.CORRECT -> Green600
                                            QuestionStatus.WRONG -> Red600
                                            QuestionStatus.EXCLUDED_OPTIONAL -> Amber600
                                            else -> Slate600
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Marked: ${if (eval.studentAnswer == "-") "Unattempted" else eval.studentAnswer} • Key: ${eval.correctAnswer}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Navy900
                                    )
                                    if (eval.topicName.isNotBlank()) {
                                        Text(text = eval.topicName, fontSize = 10.sp, color = Slate500)
                                    }
                                }
                            }

                            Surface(
                                color = when (eval.status) {
                                    QuestionStatus.CORRECT -> Green100
                                    QuestionStatus.WRONG -> Red100
                                    QuestionStatus.EXCLUDED_OPTIONAL -> Amber100
                                    else -> Slate100
                                },
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = when (eval.status) {
                                        QuestionStatus.CORRECT -> "+${eval.marksAwarded}"
                                        QuestionStatus.WRONG -> "${eval.marksAwarded}"
                                        QuestionStatus.EXCLUDED_OPTIONAL -> "OPT EXCL"
                                        else -> "0"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = when (eval.status) {
                                        QuestionStatus.CORRECT -> Green600
                                        QuestionStatus.WRONG -> Red600
                                        QuestionStatus.EXCLUDED_OPTIONAL -> Amber600
                                        else -> Slate600
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Slate400, fontSize = 10.sp)
        Text(text = value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

private fun shareOrPrintA4Pdf(context: Context, bitmap: Bitmap, filename: String) {
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
        context.startActivity(Intent.createChooser(shareIntent, "Print or Save A4 OMR PDF"))
    } catch (_: Exception) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "CP Classes A4 Evaluated OMR PDF generated.")
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share"))
    }
}

private fun shareAnnotatedImage(context: Context, bitmap: Bitmap, filename: String) {
    try {
        val cachePath = File(context.cacheDir, "annotated_omr").apply { mkdirs() }
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
        context.startActivity(Intent.createChooser(shareIntent, "Share Evaluated OMR Sheet"))
    } catch (_: Exception) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "CP Classes evaluated OMR Sheet generated.")
        }
        context.startActivity(Intent.createChooser(intent, "Share"))
    }
}

private fun buildReportShareText(res: ScannedResultEntity, student: StudentEntity?): String {
    return """
        *CP CLASSES PURNIA — OFFICIAL SCORECARD*
        ------------------------------------------
        Student: ${res.studentName}
        Roll Number: ${res.studentRollNumber}
        Batch: ${res.batchName}
        Exam: ${res.assessmentTitle}
        ------------------------------------------
        *SCORE:* ${res.score.toInt()} / ${res.maxScore.toInt()} (${res.percentage}%)
        *RANK:* #${res.rank}
        *PERCENTILE:* ${res.percentile}%
        *TOPPER MARKS:* ${res.topperScore.toInt()} (${res.topperName})
        ------------------------------------------
        Correct: ${res.correctCount}
        Wrong: ${res.wrongCount}
        Unattempted: ${res.unattemptedCount}
        Accuracy: ${res.accuracy}%
        ------------------------------------------
        CP CLASSES PURNIA • Center of Academic Excellence
        Contact: +91 98351 24567 • info@cpclasses.in
    """.trimIndent()
}

private fun sendViaWhatsApp(context: Context, phone: String, message: String) {
    try {
        val cleanPhone = phone.replace(Regex("[^0-9]"), "")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(message)}")
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        context.startActivity(Intent.createChooser(intent, "Share Report Card"))
    }
}

private fun sendViaEmail(context: Context, email: String, subject: String, body: String) {
    try {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$email")
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        context.startActivity(Intent.createChooser(intent, "Send Email"))
    }
}

private val Slate400 = Color(0xFF94A3B8)
