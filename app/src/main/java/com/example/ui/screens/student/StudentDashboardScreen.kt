package com.example.ui.screens.student

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ScannedResultEntity
import com.example.ui.components.AppTopBar
import com.example.ui.theme.*
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainAppViewModel

@Composable
fun StudentDashboardScreen(viewModel: MainAppViewModel, rollNumber: String) {
    val context = LocalContext.current
    val students by viewModel.students.collectAsState()
    val allResults by viewModel.scannedResults.collectAsState()
    val assessments by viewModel.assessments.collectAsState()

    val student = remember(students, rollNumber) {
        students.firstOrNull { it.rollNumber == rollNumber }
    }

    // Results for this student
    val studentResults: List<ScannedResultEntity> = remember(allResults, rollNumber) {
        allResults.filter { it.studentRollNumber == rollNumber }.sortedByDescending { it.timestamp }
    }

    BackHandler {
        viewModel.navigateTo(AppScreen.RoleSelection)
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Student Performance Portal",
                subtitle = "CP CLASSES PURNIA",
                showBackButton = true,
                onBackClick = { viewModel.navigateTo(AppScreen.RoleSelection) },
                actions = {
                    IconButton(
                        onClick = { viewModel.navigateTo(AppScreen.RoleSelection) },
                        modifier = Modifier.testTag("student_logout_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Log Out",
                            tint = Color.White
                        )
                    }
                }
            )
        },
        containerColor = Slate50
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Student Profile Header Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("student_profile_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Navy900),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Amber500),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = student?.name?.take(2)?.uppercase() ?: "CP",
                                    color = Navy900,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = student?.name ?: "Student Roll #$rollNumber",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Roll No: $rollNumber • ${student?.batchName ?: "General"}",
                                    color = Amber500,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Slate700)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Registered Gmail", fontSize = 11.sp, color = Slate400)
                                Text(
                                    text = student?.email ?: "Not specified",
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("WhatsApp / Phone", fontSize = 11.sp, color = Slate400)
                                Text(
                                    text = student?.whatsapp ?: student?.phone ?: "-",
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Performance Statistics Highlights
            item {
                val totalTests = studentResults.size
                val avgPercentage = if (studentResults.isNotEmpty()) {
                    studentResults.map { it.percentage }.average().toFloat()
                } else 0f
                val bestRank = if (studentResults.isNotEmpty()) {
                    studentResults.map { it.rank }.minOrNull() ?: 0
                } else 0

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Slate200)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$totalTests",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = Navy900
                            )
                            Text("Exams Taken", fontSize = 11.sp, color = Slate600)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Slate200)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${"%.1f".format(avgPercentage)}%",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = Amber600
                            )
                            Text("Avg. Score", fontSize = 11.sp, color = Slate600)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Slate200)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (bestRank > 0) "#$bestRank" else "-",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = Green600
                            )
                            Text("Best Rank", fontSize = 11.sp, color = Slate600)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your OMR Exam Scorecards",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Navy900,
                        modifier = Modifier.weight(1f)
                    )
                    Badge(
                        containerColor = Amber500,
                        contentColor = Navy900
                    ) {
                        Text(
                            text = "${studentResults.size} graded",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // Results List or Empty State
            if (studentResults.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Slate200)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.AssignmentLate,
                                contentDescription = null,
                                tint = Slate400,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No Evaluated OMR Papers Yet",
                                fontWeight = FontWeight.Bold,
                                color = Navy900,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "As soon as your CP Classes teacher scans and grades your physical OMR sheet with Roll #$rollNumber, your scorecard with rank and topper marks will appear here.",
                                color = Slate600,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(studentResults) { result ->
                    val exam = assessments.firstOrNull { it.id == result.assessmentId }
                    val topics = viewModel.getTopicResults(result.topicResultsJson)
                    val totalBatchStudents = students.count { it.batchName == result.batchName }.coerceAtLeast(1)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .testTag("student_result_card_${result.id}"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Slate200),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Title & Date
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = result.assessmentTitle,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Navy900
                                    )
                                    Text(
                                        text = "Subject: ${exam?.subject ?: "Composite"} • Set: ${result.setCode}",
                                        fontSize = 12.sp,
                                        color = Slate600
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (result.percentage >= 60f) Green50 else Amber50,
                                    border = BorderStroke(1.dp, if (result.percentage >= 60f) Green500 else Amber500)
                                ) {
                                    Text(
                                        text = "${"%.1f".format(result.percentage)}%",
                                        color = if (result.percentage >= 60f) Green700 else Amber700,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Score Comparison & Rank (Requirement 5 from user: Rank and Toppers marks mentioned)
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = Slate50,
                                border = BorderStroke(1.dp, Slate200)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Your Score", fontSize = 11.sp, color = Slate600)
                                        Text(
                                            text = "${result.score} / ${result.maxScore}",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Navy900
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Batch Rank", fontSize = 11.sp, color = Slate600)
                                        Text(
                                            text = "Rank #${result.rank} of $totalBatchStudents",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Amber600
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Topper Marks", fontSize = 11.sp, color = Slate600)
                                        Text(
                                            text = "${result.topperScore} / ${result.maxScore}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Green600
                                        )
                                    }
                                }
                            }

                            // Topic Breakdown Pills (Requirement 8a: Topic wise questions and topic wise results)
                            if (topics.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Topic-wise Performance:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Slate600
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    topics.forEach { topic ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Slate100,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = topic.topicName,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Navy900,
                                                    maxLines = 1
                                                )
                                                Text(
                                                    text = "${topic.score.toInt()} pts (${"%.0f".format(topic.accuracyPercent)}%)",
                                                    fontSize = 10.sp,
                                                    color = if (topic.accuracyPercent >= 60f) Green600 else Slate600
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Action buttons: Detailed Scorecard + WhatsApp/Email share
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.navigateTo(
                                            AppScreen.ScoreReportDetail(
                                                resultId = result.id,
                                                fromStudentView = true
                                            )
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("view_scorecard_btn_${result.id}"),
                                    colors = ButtonDefaults.buttonColors(containerColor = Navy900),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Analytics,
                                        contentDescription = null,
                                        tint = Amber500,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Full Scorecard", fontSize = 12.sp, color = Color.White)
                                }

                                // WhatsApp Share Shortcut
                                OutlinedButton(
                                    onClick = {
                                        shareOnWhatsApp(context, student?.whatsapp ?: "", result, totalBatchStudents)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Green600),
                                    border = BorderStroke(1.dp, Green500)
                                ) {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = "Share",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun shareOnWhatsApp(context: Context, phone: String, result: ScannedResultEntity, totalStudents: Int) {
    val message = """
        *CP CLASSES PURNIA - OFFICIAL SCORECARD*
        Student: ${result.studentName} (Roll: ${result.studentRollNumber})
        Exam: ${result.assessmentTitle}
        Score: ${result.score} / ${result.maxScore} (${"%.1f".format(result.percentage)}%)
        *Batch Rank: #${result.rank} of $totalStudents*
        *Topper Marks: ${result.topperScore}*
        Correct: ${result.correctCount} | Wrong: ${result.wrongCount} | Unattempted: ${result.unattemptedCount}
        
        Evaluated on CP Classes Smart OMR Engine.
    """.trimIndent()

    try {
        val cleanNumber = phone.replace(Regex("[^0-9]"), "")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = if (cleanNumber.isNotBlank()) {
                val fullNumber = if (cleanNumber.length == 10) "91$cleanNumber" else cleanNumber
                Uri.parse("https://api.whatsapp.com/send?phone=$fullNumber&text=${Uri.encode(message)}")
            } else {
                Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(message)}")
            }
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Scorecard"))
    }
}
