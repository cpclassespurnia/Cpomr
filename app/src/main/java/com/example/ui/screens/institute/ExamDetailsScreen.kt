package com.example.ui.screens.institute

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AssessmentEntity
import com.example.ui.components.AppTopBar
import com.example.ui.theme.*
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainAppViewModel

@Composable
fun ExamDetailsScreen(viewModel: MainAppViewModel, assessmentId: Long) {
    var assessment by remember { mutableStateOf<AssessmentEntity?>(null) }
    val allResults by viewModel.scannedResults.collectAsState()
    val activeStaff by viewModel.activeStaff.collectAsState()

    LaunchedEffect(assessmentId) {
        assessment = viewModel.getAssessmentById(assessmentId)
    }

    val examResults = remember(allResults, assessmentId) {
        allResults.filter { it.assessmentId == assessmentId }
    }

    val topics = remember(assessment) {
        viewModel.getTopicsForAssessment(assessment?.topicsJson ?: "")
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = assessment?.title ?: "Exam Details",
                subtitle = "CP CLASSES • EXAM CONTROL CENTER",
                showBackButton = true,
                onBackClick = { viewModel.navigateBack() }
            )
        }
    ) { paddingValues ->
        val currentAssessment = assessment
        if (currentAssessment == null) {
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
                // Header Details Card with A4 Badge
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Navy900),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = Amber500,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "ISO 216 A4 FORMAT",
                                        color = Navy900,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                                Text(
                                    text = currentAssessment.dateString,
                                    color = Slate300,
                                    fontSize = 12.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = currentAssessment.title,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "${currentAssessment.subject} • ${currentAssessment.batchName}",
                                color = Amber500,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )

                            Spacer(modifier = Modifier.height(14.dp))
                            Divider(color = Navy700)
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                InfoColumn(label = "QUESTIONS", value = "${currentAssessment.totalQuestions} Q")
                                InfoColumn(label = "MARKING", value = "+${currentAssessment.marksPerCorrect} / -${currentAssessment.negativeMarksPerWrong}")
                                InfoColumn(label = "MAX MARKS", value = "${(currentAssessment.totalQuestions * currentAssessment.marksPerCorrect).toInt()}")
                                InfoColumn(label = "EVALUATED", value = "${examResults.size}")
                            }
                        }
                    }
                }

                // Answer Key Section (Requirement 6 & 7: Assign later by tapping bubbles on OMR)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.5.dp, if (currentAssessment.answerKeyConfigured) Green500 else Amber500)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (currentAssessment.answerKeyConfigured) Icons.Default.CheckCircle else Icons.Default.Pending,
                                        contentDescription = null,
                                        tint = if (currentAssessment.answerKeyConfigured) Green600 else Amber600,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Answer Key Section",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Navy900
                                        )
                                        Text(
                                            text = if (currentAssessment.answerKeyConfigured) "Configured (Ready for Scanner)" else "Not Assigned (Tap bubbles to configure)",
                                            fontSize = 11.sp,
                                            color = if (currentAssessment.answerKeyConfigured) Green600 else Amber600,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Surface(
                                    color = if (currentAssessment.answerKeyConfigured) Green100 else Amber100,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (currentAssessment.answerKeyConfigured) "READY" else "PENDING",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (currentAssessment.answerKeyConfigured) Green600 else Amber600,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Answer keys can be assigned for Sets A, B, C, D by tapping directly on the generated A4 OMR bubbles.",
                                fontSize = 12.sp,
                                color = Slate600
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    viewModel.navigateTo(AppScreen.AnswerKeyEditor(currentAssessment.id, "A"))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("open_answer_key_editor_btn"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (currentAssessment.answerKeyConfigured) Navy700 else Amber600
                                ),
                                shape = RoundedCornerShape(8.dp),
                                enabled = activeStaff?.canEditAnswerKeys != false
                            ) {
                                Icon(Icons.Default.TouchApp, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (currentAssessment.answerKeyConfigured) "Edit Answer Key on OMR Sheet" else "Assign Answer Key on OMR Sheet",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Topic-Wise Breakdown & Optional Rules Card (Requirement 8a & 8b)
                if (topics.isNotEmpty() || currentAssessment.optionalRulesJson.isNotBlank()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Slate300)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "TOPIC-WISE STRUCTURE & OPTIONAL RULES",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate600,
                                    letterSpacing = 1.sp
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                topics.forEach { topic ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "${topic.name} (Q${topic.startQuestion}–Q${topic.endQuestion})",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Navy900
                                            )
                                            if (topic.maxAttempts != null) {
                                                Text(
                                                    text = "Optional limit: evaluate maximum ${topic.maxAttempts} questions",
                                                    fontSize = 11.sp,
                                                    color = Amber600
                                                )
                                            }
                                        }

                                        Surface(
                                            color = Blue50,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "${topic.endQuestion - topic.startQuestion + 1} Questions",
                                                fontSize = 11.sp,
                                                color = Navy700,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Action Buttons: Print Blank A4 OMR & Scan Physical OMR
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.navigateTo(AppScreen.OmrTemplateViewer(currentAssessment.id))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("view_a4_omr_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = Navy900),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, tint = Amber500)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Blank A4 OMR", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                viewModel.navigateTo(AppScreen.OmrScanner(currentAssessment.id))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("scan_omr_camera_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = Amber500),
                            shape = RoundedCornerShape(10.dp),
                            enabled = activeStaff?.canScanOmr != false
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Navy900)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan OMR", color = Navy900, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Leaderboard & Item Analysis Navigation Chips
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.navigateTo(AppScreen.BatchLeaderboard(currentAssessment.id)) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Leaderboard, contentDescription = null, tint = Navy700, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rank List", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { viewModel.navigateTo(AppScreen.ItemAnalysis(currentAssessment.id)) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Analytics, contentDescription = null, tint = Navy700, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Item Analysis", fontSize = 12.sp)
                        }
                    }
                }

                // Scanned Results Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SCANNED OMR RESULTS (${examResults.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate600,
                            letterSpacing = 1.sp
                        )
                        if (examResults.isNotEmpty()) {
                            Text(
                                text = "Topper: ${examResults.maxOfOrNull { it.score }?.toInt()} marks",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Green600
                            )
                        }
                    }
                }

                if (examResults.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Slate300)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = Slate400, modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No OMR sheets scanned yet for this test.",
                                    color = Slate600,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Use the 'Scan OMR' button to grade physical sheets with camera.",
                                    color = Slate500,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                } else {
                    items(examResults) { res ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Slate300)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = Navy900,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = res.studentRollNumber,
                                                color = Amber500,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = res.studentName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Navy900
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Score: ${res.score} / ${res.maxScore} (${res.percentage}%) • Rank: #${res.rank}",
                                        fontSize = 12.sp,
                                        color = Slate600
                                    )
                                    Text(
                                        text = "Correct: ${res.correctCount} | Wrong: ${res.wrongCount} | Unattempted: ${res.unattemptedCount}",
                                        fontSize = 11.sp,
                                        color = Slate500
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        viewModel.navigateTo(AppScreen.ScoreReportDetail(res.id, false))
                                    }
                                ) {
                                    Text("Report Card →", fontSize = 12.sp, color = Blue500, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Delete Assessment Button if Admin
                if (activeStaff?.canDeleteResults == true) {
                    item {
                        OutlinedButton(
                            onClick = { viewModel.deleteAssessment(currentAssessment.id) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Red600),
                            border = BorderStroke(1.dp, Red500)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete Assessment & All Results")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(text = value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

private val Slate400 = Color(0xFF94A3B8)
