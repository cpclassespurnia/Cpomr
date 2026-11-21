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
import com.example.data.model.StudentEntity
import com.example.ui.components.AppTopBar
import com.example.ui.theme.*
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainAppViewModel

/**
 * Requirement 9: Admin can edit the roll number and question answers during OMR scan verification
 */
@Composable
fun ScanVerificationScreen(
    viewModel: MainAppViewModel,
    assessmentId: Long,
    roll: String,
    setCode: String,
    markedAnswers: List<String>
) {
    val registeredStudents by viewModel.students.collectAsState()

    var studentRoll by remember { mutableStateOf(roll) }
    var currentAnswers by remember { mutableStateOf(markedAnswers.toMutableList()) }
    var isEditingRoll by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var existingResultForRoll by remember { mutableStateOf<com.example.data.model.ScannedResultEntity?>(null) }

    LaunchedEffect(studentRoll) {
        if (studentRoll.isNotBlank()) {
            existingResultForRoll = viewModel.checkExistingResult(assessmentId, studentRoll.trim())
        } else {
            existingResultForRoll = null
        }
    }

    val matchedStudent = remember(studentRoll, registeredStudents) {
        registeredStudents.firstOrNull { it.rollNumber.equals(studentRoll.trim(), ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Verify & Edit Scanned OMR",
                subtitle = "STUDENT IDENTITY & ANSWERS AUDIT",
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${currentAnswers.count { it != "-" }} Attempted",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Navy900
                        )
                        Text(
                            text = "Roll #$studentRoll • Set $setCode",
                            fontSize = 11.sp,
                            color = Slate600
                        )
                    }

                    Button(
                        onClick = {
                            isSaving = true
                            viewModel.confirmAndSaveGrade(
                                assessmentId = assessmentId,
                                studentRoll = studentRoll.trim(),
                                setCode = setCode,
                                markedAnswers = currentAnswers
                            ) { result ->
                                isSaving = false
                                viewModel.navigateTo(AppScreen.ScoreReportDetail(result.id, false))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Green600),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isSaving && studentRoll.isNotBlank(),
                        modifier = Modifier.testTag("confirm_save_grade_btn")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Confirm & Grade OMR", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
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
            // Requirement 9: Editable Roll Number & Matched Student Identity Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Navy900),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Badge, contentDescription = null, tint = Amber500)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "STUDENT ROLL & PROFILE",
                                    color = Amber500,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            TextButton(
                                onClick = { isEditingRoll = !isEditingRoll }
                            ) {
                                Icon(
                                    imageVector = if (isEditingRoll) Icons.Default.Check else Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = Amber500,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isEditingRoll) "Done" else "Edit Roll",
                                    color = Amber500,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Editable Roll Input Field (Requirement 9)
                        if (isEditingRoll) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = studentRoll,
                                onValueChange = { studentRoll = it },
                                label = { Text("Edit Student Roll Number", color = Slate300) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("edit_scanned_roll_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Amber500,
                                    unfocusedBorderColor = Slate400
                                ),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val student = matchedStudent
                        if (student != null) {
                            Text(
                                text = student.name,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Roll: ${student.rollNumber} • ${student.batchName}",
                                color = Slate300,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Gmail: ${student.email} • WhatsApp: ${student.whatsapp}",
                                color = Blue100,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        } else {
                            Text(
                                text = "Roll #$studentRoll (Not Registered in Database)",
                                color = Amber500,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Will evaluate paper as Guest student. You can change roll above to match registered student.",
                                color = Slate300,
                                fontSize = 12.sp
                            )
                        }

                        // Quick selection from registered students
                        if (isEditingRoll) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Or select registered student:", color = Slate300, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                registeredStudents.take(3).forEach { st ->
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                studentRoll = st.rollNumber
                                                isEditingRoll = false
                                            },
                                        shape = RoundedCornerShape(6.dp),
                                        color = Slate800,
                                        border = BorderStroke(1.dp, Slate600)
                                    ) {
                                        Column(modifier = Modifier.padding(6.dp)) {
                                            Text(st.name.take(12), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text("#${st.rollNumber}", color = Amber500, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // Duplicate scan warning badge
                        existingResultForRoll?.let { ex ->
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = Amber500.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, Amber500),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Amber500, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Roll #$studentRoll already scanned on ${ex.scannedAtFormatted}",
                                            color = Amber500,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Previous score: ${ex.score.toInt()}/${ex.maxScore.toInt()} (Rank #${ex.rank}). Saving will update this submission.",
                                            color = Slate300,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DETECTED ANSWERS (TAP BUBBLE TO EDIT)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate600,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "A, B, C, D or — (unattempted)",
                        fontSize = 10.sp,
                        color = Slate500
                    )
                }
            }

            // Requirement 9: Admin can edit answers of questions
            itemsIndexed(currentAnswers) { idx, ans ->
                val qNum = idx + 1
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("question_row_$qNum"),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Slate300),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Q$qNum",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Navy900,
                            modifier = Modifier.width(36.dp)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("A", "B", "C", "D").forEach { opt ->
                                val isSelected = (ans == opt)
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Navy900 else Color.White)
                                        .border(
                                            1.dp,
                                            if (isSelected) Amber500 else Slate400,
                                            CircleShape
                                        )
                                        .clickable {
                                            val copy = currentAnswers.toMutableList()
                                            copy[idx] = if (copy[idx] == opt) "-" else opt
                                            currentAnswers = copy
                                        }
                                        .testTag("verify_bubble_q${qNum}_$opt"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = opt,
                                        color = if (isSelected) Amber500 else Slate800,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Unattempted toggle
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(if (ans == "-") Slate200 else Color.White)
                                    .border(1.dp, Slate300, CircleShape)
                                    .clickable {
                                        val copy = currentAnswers.toMutableList()
                                        copy[idx] = "-"
                                        currentAnswers = copy
                                    }
                                    .testTag("verify_unattempted_q$qNum"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "—",
                                    color = Slate600,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
