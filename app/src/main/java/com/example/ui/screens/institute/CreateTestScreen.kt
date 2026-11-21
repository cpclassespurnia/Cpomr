package com.example.ui.screens.institute

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TopicDefinition
import com.example.ui.components.AppTopBar
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainAppViewModel

/**
 * Requirement 2, 3, 5, 6, 7, 8:
 * - Direct custom 1 to 200 questions
 * - Custom roll number digits (1 to 10 digits)
 * - Optional attempts does NOT redundantly ask for total questions again
 * - Does not require upfront answer key (configured later by tapping OMR bubbles)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateTestScreen(viewModel: MainAppViewModel) {
    val batches by viewModel.batches.collectAsState()

    var testTitle by remember { mutableStateOf("Minor Test 12th eng") }
    var selectedSubject by remember { mutableStateOf("Physics & Chemistry") }
    var selectedBatchId by remember { mutableStateOf(batches.firstOrNull()?.id ?: 1L) }
    var selectedBatchName by remember { mutableStateOf(batches.firstOrNull()?.name ?: "12th CP 26-27") }

    // Requirement 2: Custom Number of Questions from 1 to 200
    var questionsCountInput by remember { mutableStateOf("35") }
    var questionsCount by remember { mutableIntStateOf(35) }

    // Marking Scheme
    var marksPerCorrectInput by remember { mutableStateOf("4.0") }
    var negativeMarksInput by remember { mutableStateOf("1.0") }
    var unattemptedMarksInput by remember { mutableStateOf("0.0") }

    // Requirement 3: Custom roll number digits (1 to 10)
    var rollDigitsInput by remember { mutableStateOf("4") }
    var rollDigits by remember { mutableIntStateOf(4) }

    // Header candidate info toggles
    var includeName by remember { mutableStateOf(true) }
    var includeRoll by remember { mutableStateOf(true) }
    var includeDate by remember { mutableStateOf(true) }
    var includeBatch by remember { mutableStateOf(true) }
    var includePhone by remember { mutableStateOf(true) }

    // Requirement 5 & 8: Optional Attempt Rule (No redundant question count prompt!)
    var hasOptionalAttempts by remember { mutableStateOf(false) }
    var optionalMaxAttemptInput by remember { mutableStateOf("30") }

    // Topics list
    var hasTopics by remember { mutableStateOf(false) }
    var topicsList by remember { mutableStateOf<List<TopicDefinition>>(emptyList()) }
    var showAddTopicDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Create New Assessment",
                subtitle = "CP CLASSES PURNIA • A4 OMR",
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // A4 Badge Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Navy900,
                border = BorderStroke(1.dp, Amber500)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Amber500, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, tint = Navy900)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Standard ISO 216 A4 Sheet (210 × 297 mm)",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Answer keys can be set later by tapping on the generated OMR bubbles.",
                            color = Amber500,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Section 1: Basic Test Information
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Slate300)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "EXAM DETAILS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate600,
                        letterSpacing = 1.sp
                    )

                    OutlinedTextField(
                        value = testTitle,
                        onValueChange = { testTitle = it },
                        label = { Text("Exam / Test Title") },
                        placeholder = { Text("e.g. Minor Test 12th eng") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_title_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = selectedSubject,
                        onValueChange = { selectedSubject = it },
                        label = { Text("Subject / Discipline") },
                        placeholder = { Text("e.g. Physics, Chemistry, Biology, Math") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("subject_input"),
                        singleLine = true
                    )

                    Text(
                        text = "Assigned Batch / Class:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate700
                    )

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        batches.forEach { batch ->
                            FilterChip(
                                selected = selectedBatchId == batch.id,
                                onClick = {
                                    selectedBatchId = batch.id
                                    selectedBatchName = batch.name
                                },
                                label = { Text(batch.name, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            // Section 2: Total Questions (1 to 200)
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                                text = "QUESTION COUNT (1 TO 200)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate600,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Choose any number of questions for this exam",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Amber100
                        ) {
                            Text(
                                text = "$questionsCount Questions",
                                color = Navy900,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = questionsCountInput,
                            onValueChange = { input ->
                                questionsCountInput = input
                                val parsed = input.toIntOrNull()
                                if (parsed != null) {
                                    questionsCount = parsed.coerceIn(1, 200)
                                }
                            },
                            label = { Text("Type 1–200") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("question_count_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Slider(
                            value = questionsCount.toFloat(),
                            onValueChange = {
                                questionsCount = it.toInt()
                                questionsCountInput = questionsCount.toString()
                            },
                            valueRange = 1f..200f,
                            modifier = Modifier
                                .weight(2f)
                                .testTag("question_count_slider")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Quick Presets:", fontSize = 11.sp, color = Slate500)
                    FlowRow(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(25, 30, 35, 50, 75, 90, 100, 150, 180, 200).forEach { preset ->
                            SuggestionChip(
                                onClick = {
                                    questionsCount = preset
                                    questionsCountInput = preset.toString()
                                },
                                label = { Text("$preset Q", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            // Section 3: Optional Attempts Rule (Requirement 5 - Streamlined, no duplicate question prompt!)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Slate300)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "OPTIONAL ATTEMPTS RULE",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate600,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Allow students to attempt fewer questions than total $questionsCount",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }

                        Switch(
                            checked = hasOptionalAttempts,
                            onCheckedChange = { hasOptionalAttempts = it }
                        )
                    }

                    if (hasOptionalAttempts) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Slate100,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Rule: Attempt any X out of $questionsCount questions",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Navy900
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                OutlinedTextField(
                                    value = optionalMaxAttemptInput,
                                    onValueChange = { optionalMaxAttemptInput = it },
                                    label = { Text("Number of Questions to Attempt / Evaluate") },
                                    placeholder = { Text("e.g. 30 out of $questionsCount") },
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true
                                )

                                val maxAttVal = optionalMaxAttemptInput.toIntOrNull() ?: questionsCount
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Students can choose to answer any $maxAttVal questions. Only the first $maxAttVal answered will be graded; any extra answered will be excluded.",
                                    fontSize = 11.sp,
                                    color = Slate600
                                )
                            }
                        }
                    }
                }
            }

            // Section 4: Roll Number Digits (Requirement 3: Custom digits 1 to 10)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Slate300)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ROLL NUMBER MATRIX DIGITS (CUSTOM)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate600,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Define the exact number of digits for roll number bubbles on the OMR sheet (1 to 10)",
                        fontSize = 11.sp,
                        color = Slate500
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = rollDigitsInput,
                            onValueChange = { input ->
                                rollDigitsInput = input
                                val parsed = input.toIntOrNull()
                                if (parsed != null) {
                                    rollDigits = parsed.coerceIn(1, 10)
                                }
                            },
                            label = { Text("Digits Count (1–10)") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("roll_digits_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Blue100,
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            Text(
                                text = "$rollDigits-Digit Roll Grid",
                                color = Navy900,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Standard Presets:", fontSize = 11.sp, color = Slate500)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (2..8).forEach { d ->
                            FilterChip(
                                selected = rollDigits == d,
                                onClick = {
                                    rollDigits = d
                                    rollDigitsInput = d.toString()
                                },
                                label = { Text("$d Digits") }
                            )
                        }
                    }
                }
            }

            // Section 5: Marking Scheme
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Slate300)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "MARKING SCHEME",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate600,
                        letterSpacing = 1.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = marksPerCorrectInput,
                            onValueChange = { marksPerCorrectInput = it },
                            label = { Text("Correct (+)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = negativeMarksInput,
                            onValueChange = { negativeMarksInput = it },
                            label = { Text("Wrong (-)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = unattemptedMarksInput,
                            onValueChange = { unattemptedMarksInput = it },
                            label = { Text("Unattempted") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                }
            }

            // Generate Button
            Button(
                onClick = {
                    val correctMarks = marksPerCorrectInput.toFloatOrNull() ?: 4.0f
                    val wrongMarks = negativeMarksInput.toFloatOrNull() ?: 1.0f
                    val unattemptedMarks = unattemptedMarksInput.toFloatOrNull() ?: 0.0f
                    val maxAtt = if (hasOptionalAttempts) optionalMaxAttemptInput.toIntOrNull() else null

                    val finalTopics = if (hasOptionalAttempts && maxAtt != null) {
                        listOf(TopicDefinition("Full Paper", 1, questionsCount, maxAtt))
                    } else {
                        topicsList
                    }

                    val optionalDescription = if (hasOptionalAttempts && maxAtt != null) {
                        "Attempt any $maxAtt out of $questionsCount questions"
                    } else ""

                    viewModel.createAssessmentWithoutKey(
                        title = testTitle.ifBlank { "CP Assessment ($questionsCount Q)" },
                        batchId = selectedBatchId,
                        batchName = selectedBatchName,
                        subject = selectedSubject.ifBlank { "General" },
                        totalQuestions = questionsCount,
                        marksPerCorrect = correctMarks,
                        negativeMarksPerWrong = wrongMarks,
                        marksUnattempted = unattemptedMarks,
                        rollDigits = rollDigits,
                        dateString = "2026-10-23",
                        includeNameField = includeName,
                        includeRollField = includeRoll,
                        includeDateField = includeDate,
                        includeBatchField = includeBatch,
                        includePhoneField = includePhone,
                        topics = finalTopics,
                        optionalRulesDescription = optionalDescription
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("generate_omr_button"),
                colors = ButtonDefaults.buttonColors(containerColor = Navy900),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Print, contentDescription = null, tint = Amber500)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Generate A4 OMR Sheet ($questionsCount Q)",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
