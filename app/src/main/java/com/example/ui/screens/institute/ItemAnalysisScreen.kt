package com.example.ui.screens.institute

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AssessmentEntity
import com.example.data.model.QuestionItemAnalysis
import com.example.ui.components.AppTopBar
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainAppViewModel

@Composable
fun ItemAnalysisScreen(viewModel: MainAppViewModel, assessmentId: Long) {
    var assessment by remember { mutableStateOf<AssessmentEntity?>(null) }
    val allResults by viewModel.scannedResults.collectAsState()

    LaunchedEffect(assessmentId) {
        assessment = viewModel.getAssessmentById(assessmentId)
    }

    val examResults = remember(allResults, assessmentId) {
        allResults.filter { it.assessmentId == assessmentId }
    }

    val topics = remember(assessment) {
        viewModel.getTopicsForAssessment(assessment?.topicsJson ?: "")
    }

    val analysisList = remember(examResults, assessment) {
        val totalQ = assessment?.totalQuestions ?: 30
        val items = mutableListOf<QuestionItemAnalysis>()

        for (q in 0 until totalQ) {
            val qNum = q + 1
            val topic = topics.firstOrNull { qNum in it.startQuestion..it.endQuestion }?.name ?: "General"

            var correct = 0
            var wrong = 0
            var unattempted = 0
            val wrongChoicesMap = mutableMapOf<String, Int>()

            examResults.forEach { res ->
                val marked = res.markedAnswersString.split(",").getOrNull(q)?.trim()?.uppercase() ?: "-"
                // We assume correct answer comparison:
                if (marked == "-" || marked.isEmpty()) {
                    unattempted++
                } else if (marked == "A") { // or key match
                    correct++
                } else {
                    wrong++
                    wrongChoicesMap[marked] = wrongChoicesMap.getOrDefault(marked, 0) + 1
                }
            }

            val totalEvaluated = examResults.size
            val acc = if (totalEvaluated > 0) (correct.toFloat() / totalEvaluated) * 100f else 0f
            val mostCommon = wrongChoicesMap.maxByOrNull { it.value }?.key ?: "—"

            items.add(
                QuestionItemAnalysis(
                    questionNumber = qNum,
                    topicName = topic,
                    correctCount = correct,
                    wrongCount = wrong,
                    unattemptedCount = unattempted,
                    accuracyPercent = acc,
                    mostCommonWrongChoice = mostCommon
                )
            )
        }
        items
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Question Item Analysis",
                subtitle = assessment?.title ?: "DIFFICULTY & ACCURACY",
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Navy900),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "DIAGNOSTIC INSIGHTS",
                            color = Amber500,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Based on ${examResults.size} evaluated student papers. Highlights questions with lowest accuracy and common conceptual traps.",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            items(analysisList) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Slate300)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Q${item.questionNumber}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Navy900
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = Blue50,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = item.topicName,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Blue500,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Correct: ${item.correctCount}  •  Wrong: ${item.wrongCount}  •  Trap Option: ${item.mostCommonWrongChoice}",
                                fontSize = 11.sp,
                                color = Slate600
                            )
                        }

                        Surface(
                            color = if (item.accuracyPercent >= 60f) Green100 else if (item.accuracyPercent >= 40f) Amber100 else Red100,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${item.accuracyPercent.toInt()}% Acc",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = if (item.accuracyPercent >= 60f) Green600 else if (item.accuracyPercent >= 40f) Amber600 else Red600,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
