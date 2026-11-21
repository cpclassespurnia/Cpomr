package com.example.ui.screens.institute

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
fun BatchLeaderboardScreen(viewModel: MainAppViewModel, assessmentId: Long) {
    var assessment by remember { mutableStateOf<AssessmentEntity?>(null) }
    val allResults by viewModel.scannedResults.collectAsState()

    LaunchedEffect(assessmentId) {
        assessment = viewModel.getAssessmentById(assessmentId)
    }

    val examResults = remember(allResults, assessmentId) {
        allResults.filter { it.assessmentId == assessmentId }
            .sortedByDescending { it.score }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Batch Rank List & Topper Analysis",
                subtitle = assessment?.title ?: "CP CLASSES PURNIA",
                showBackButton = true,
                onBackClick = { viewModel.navigateBack() }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Slate50)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Topper Header Banner
            val topper = examResults.firstOrNull()
            if (topper != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Navy900),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Amber500),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Navy900, modifier = Modifier.size(28.dp))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "TOPPER: ${topper.studentName} (Rank #1)",
                                    color = Amber500,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${topper.score.toInt()} Marks (${topper.percentage}%)",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "Accuracy: ${topper.accuracy}% • Roll: ${topper.studentRollNumber}",
                                    color = Slate300,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "STUDENTS RANKED BY MARKS (${examResults.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate600,
                    letterSpacing = 1.sp
                )
            }

            itemsIndexed(examResults) { index, res ->
                val rank = index + 1
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.navigateTo(AppScreen.ScoreReportDetail(res.id, false))
                        }
                        .testTag("leaderboard_item_$rank"),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(
                        if (rank <= 3) 1.5.dp else 1.dp,
                        when (rank) {
                            1 -> Amber500
                            2 -> Color(0xFFC0C0C0) // Silver
                            3 -> Color(0xFFCD7F32) // Bronze
                            else -> Slate300
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    when (rank) {
                                        1 -> Amber500
                                        2 -> Color(0xFFE2E8F0)
                                        3 -> Color(0xFFFED7AA)
                                        else -> Slate100
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "#$rank",
                                color = if (rank == 1) Navy900 else Slate800,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = res.studentName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Navy900
                            )
                            Text(
                                text = "Roll: ${res.studentRollNumber} • Correct: ${res.correctCount} | Wrong: ${res.wrongCount}",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${res.score.toInt()} pts",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = Navy900
                            )
                            Text(
                                text = "${res.accuracy}% Acc",
                                fontSize = 11.sp,
                                color = Blue500,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
