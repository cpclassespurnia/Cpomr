package com.example.ui.screens.institute

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AssessmentEntity
import com.example.data.model.TopicDefinition
import com.example.ui.components.AppTopBar
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainAppViewModel

@Composable
fun AnswerKeyEditorScreen(
    viewModel: MainAppViewModel,
    assessmentId: Long,
    initialSetCode: String = "A"
) {
    var assessment by remember { mutableStateOf<AssessmentEntity?>(null) }
    var selectedSet by remember { mutableStateOf(initialSetCode) }
    var showSavedSnackbar by remember { mutableStateOf(false) }

    val editingKey by viewModel.editingAnswerKey.collectAsState()
    val setCodes = listOf("A", "B", "C", "D")

    LaunchedEffect(assessmentId, selectedSet) {
        assessment = viewModel.getAssessmentById(assessmentId)
        viewModel.loadAnswerKeyForEditor(assessmentId, selectedSet)
    }

    val topics = remember(assessment) {
        viewModel.getTopicsForAssessment(assessment?.topicsJson ?: "")
    }

    val totalQuestions = assessment?.totalQuestions ?: 30
    val assignedCount = editingKey.count { it != "-" }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Assign Answer Key (Interactive OMR)",
                subtitle = "TAP BUBBLES ON GENERATED SHEET",
                showBackButton = true,
                onBackClick = { viewModel.navigateBack() }
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "$assignedCount / $totalQuestions Assigned",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Navy900
                        )
                        Text(
                            text = if (assignedCount == totalQuestions) "All keys assigned" else "Unassigned will default to A",
                            fontSize = 11.sp,
                            color = if (assignedCount == totalQuestions) Green600 else Amber600
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.saveEditingAnswerKey(assessmentId) {
                                showSavedSnackbar = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Green600),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("save_answer_key_button")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Answer Key (Set $selectedSet)", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Slate50)
        ) {
            // Set Selection Tabs & Quick Tools Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Navy900
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SELECT PAPER SET:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Amber500,
                            letterSpacing = 1.sp
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            setCodes.forEach { setCode ->
                                FilterChip(
                                    selected = selectedSet == setCode,
                                    onClick = {
                                        selectedSet = setCode
                                        viewModel.loadAnswerKeyForEditor(assessmentId, setCode)
                                    },
                                    label = { Text("Set $setCode", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Amber500,
                                        selectedLabelColor = Navy900,
                                        containerColor = Navy800,
                                        labelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Progress bar
                    LinearProgressIndicator(
                        progress = { if (totalQuestions > 0) assignedCount.toFloat() / totalQuestions else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Amber500,
                        trackColor = Navy800,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick assignment tools
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tap any bubble to mark correct answer:",
                            color = Slate300,
                            fontSize = 11.sp
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(
                                onClick = { viewModel.fillAllRemainingWithOption("A") },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Fill Rem (A)", fontSize = 10.sp)
                            }
                            FilledTonalButton(
                                onClick = { viewModel.clearAllAnswers() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Clear All", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            if (showSavedSnackbar) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Green100),
                    border = BorderStroke(1.dp, Green500)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Green600)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Answer Key for Set $selectedSet successfully saved! OMR scanner will use this for automated evaluation.",
                            fontSize = 12.sp,
                            color = Green600,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showSavedSnackbar = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Green600)
                        }
                    }
                }
            }

            // Interactive OMR Bubble Grid
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(editingKey) { qIndex, currentAnswer ->
                    val qNum = qIndex + 1

                    // Topic / Section Header banner if start of topic
                    val topic = topics.firstOrNull { it.startQuestion == qNum }
                    if (topic != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 4.dp),
                            color = Blue100,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Blue500.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SECTION: ${topic.name.uppercase()}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Navy900
                                )
                                if (topic.maxAttempts != null) {
                                    Surface(
                                        color = Amber500,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "OPTIONAL: ATTEMPT ${topic.maxAttempts}/${topic.endQuestion - topic.startQuestion + 1}",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Navy900,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Question Row with Tappable Bubbles
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("question_row_$qNum"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (currentAnswer != "-") Color.White else Slate100
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (currentAnswer != "-") Green500.copy(alpha = 0.6f) else Slate300
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Question Number Badge
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(if (currentAnswer != "-") Navy900 else Slate300),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$qNum",
                                        color = if (currentAnswer != "-") Amber500 else Slate700,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (currentAnswer != "-") "Answer: $currentAnswer" else "Not set",
                                    fontSize = 12.sp,
                                    color = if (currentAnswer != "-") Green600 else Slate500,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Tappable Bubbles (A) (B) (C) (D)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("A", "B", "C", "D").forEach { opt ->
                                    val isSelected = (currentAnswer == opt)
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) Green600 else Color.White)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.2.dp,
                                                color = if (isSelected) Green600 else Slate400,
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                viewModel.setAnswerForQuestion(qIndex, opt)
                                            }
                                            .testTag("bubble_q${qNum}_$opt"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = opt,
                                            color = if (isSelected) Color.White else Slate800,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold
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
}

private val Slate400: Color = Color(0xFF94A3B8)
