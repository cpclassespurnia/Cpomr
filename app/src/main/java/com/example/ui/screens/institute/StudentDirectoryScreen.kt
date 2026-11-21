package com.example.ui.screens.institute

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StudentEntity
import com.example.ui.components.AppTopBar
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainAppViewModel

/**
 * Requirement 2: Students directory with edit and delete capabilities
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudentDirectoryScreen(viewModel: MainAppViewModel) {
    val students by viewModel.students.collectAsState()
    val batches by viewModel.batches.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showAddStudentDialog by remember { mutableStateOf(false) }
    var studentToEdit by remember { mutableStateOf<StudentEntity?>(null) }
    var studentToDelete by remember { mutableStateOf<StudentEntity?>(null) }

    val filteredStudents = remember(students, searchQuery) {
        if (searchQuery.isBlank()) students
        else students.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.rollNumber.contains(searchQuery, ignoreCase = true) ||
            it.email.contains(searchQuery, ignoreCase = true) ||
            it.batchName.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Student Directory & Whitelist",
                subtitle = "CP CLASSES PURNIA",
                showBackButton = true,
                onBackClick = { viewModel.navigateBack() }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddStudentDialog = true },
                containerColor = Amber500,
                contentColor = Navy900,
                modifier = Modifier.testTag("add_student_fab")
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add Student")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Slate50)
                .padding(16.dp)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("student_search_input"),
                placeholder = { Text("Search by name, roll no or Gmail...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Slate500) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Whitelist notice
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Blue50,
                border = BorderStroke(0.8.dp, Blue500.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Email, contentDescription = null, tint = Blue500, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Students can only log in using Gmail IDs listed below. Adding/editing a student here controls their access permissions.",
                        fontSize = 11.sp,
                        color = Navy900
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredStudents) { student ->
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("student_item_${student.rollNumber}"),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Slate300),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Navy900),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = student.rollNumber.takeLast(2),
                                    color = Amber500,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = student.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Navy900
                                )
                                Text(
                                    text = "Roll: ${student.rollNumber} • ${student.batchName}",
                                    fontSize = 12.sp,
                                    color = Slate600
                                )
                                Text(
                                    text = "Gmail: ${student.email}",
                                    fontSize = 11.sp,
                                    color = Blue500,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (student.whatsapp.isNotBlank()) {
                                    Text(
                                        text = "WhatsApp: ${student.whatsapp}",
                                        fontSize = 11.sp,
                                        color = Slate500
                                    )
                                }
                            }

                            // Edit and Delete Buttons (Requirement 2)
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(
                                    onClick = { studentToEdit = student },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Student", tint = Navy700, modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = { studentToDelete = student },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Student", tint = Red500, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Student Dialog
    if (showAddStudentDialog) {
        var name by remember { mutableStateOf("") }
        var roll by remember { mutableStateOf("10${students.size + 1}") }
        var email by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("+91 ") }
        var whatsapp by remember { mutableStateOf("+91 ") }
        var selectedBatchId by remember { mutableStateOf(batches.firstOrNull()?.id ?: 1L) }
        var selectedBatchName by remember { mutableStateOf(batches.firstOrNull()?.name ?: "Batch") }

        AlertDialog(
            onDismissRequest = { showAddStudentDialog = false },
            title = { Text("Register New Student", fontWeight = FontWeight.Bold, color = Navy900) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = roll,
                        onValueChange = { roll = it },
                        label = { Text("Roll Number (e.g. 101, 102...)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Student Gmail (For Login Whitelist)") },
                        placeholder = { Text("student@gmail.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = whatsapp,
                        onValueChange = { whatsapp = it },
                        label = { Text("WhatsApp Number (For OTP / Reports)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text("Assign to Batch:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        batches.forEach { b ->
                            FilterChip(
                                selected = selectedBatchId == b.id,
                                onClick = {
                                    selectedBatchId = b.id
                                    selectedBatchName = b.name
                                },
                                label = { Text(b.name.take(15), fontSize = 10.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank() && roll.isNotBlank() && email.isNotBlank()) {
                            viewModel.addNewStudent(
                                roll = roll.trim(),
                                name = name.trim(),
                                batchId = selectedBatchId,
                                batchName = selectedBatchName,
                                email = email.trim().lowercase(),
                                phone = phone.trim(),
                                whatsapp = whatsapp.trim()
                            )
                            showAddStudentDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Navy900),
                    enabled = name.isNotBlank() && roll.isNotBlank() && email.isNotBlank()
                ) {
                    Text("Register & Whitelist")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddStudentDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Edit Student Dialog (Requirement 2)
    studentToEdit?.let { currentStudent ->
        var editName by remember(currentStudent) { mutableStateOf(currentStudent.name) }
        var editRoll by remember(currentStudent) { mutableStateOf(currentStudent.rollNumber) }
        var editEmail by remember(currentStudent) { mutableStateOf(currentStudent.email) }
        var editPhone by remember(currentStudent) { mutableStateOf(currentStudent.phone) }
        var editWhatsapp by remember(currentStudent) { mutableStateOf(currentStudent.whatsapp) }
        var editBatchId by remember(currentStudent) { mutableStateOf(currentStudent.batchId) }
        var editBatchName by remember(currentStudent) { mutableStateOf(currentStudent.batchName) }

        AlertDialog(
            onDismissRequest = { studentToEdit = null },
            title = { Text("Edit Student Details", fontWeight = FontWeight.Bold, color = Navy900) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Student Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editRoll,
                        onValueChange = { editRoll = it },
                        label = { Text("Roll Number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editEmail,
                        onValueChange = { editEmail = it },
                        label = { Text("Gmail (Login Whitelist)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editWhatsapp,
                        onValueChange = { editWhatsapp = it },
                        label = { Text("WhatsApp Number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text("Batch Assignment:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        batches.forEach { b ->
                            FilterChip(
                                selected = editBatchId == b.id,
                                onClick = {
                                    editBatchId = b.id
                                    editBatchName = b.name
                                },
                                label = { Text(b.name.take(15), fontSize = 10.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isNotBlank() && editRoll.isNotBlank()) {
                            viewModel.updateStudent(
                                currentStudent.copy(
                                    name = editName.trim(),
                                    rollNumber = editRoll.trim(),
                                    email = editEmail.trim().lowercase(),
                                    phone = editPhone.trim(),
                                    whatsapp = editWhatsapp.trim(),
                                    batchId = editBatchId,
                                    batchName = editBatchName
                                )
                            )
                            studentToEdit = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Navy900)
                ) {
                    Text("Update Details")
                }
            },
            dismissButton = {
                TextButton(onClick = { studentToEdit = null }) { Text("Cancel") }
            }
        )
    }

    // Delete Student Dialog
    studentToDelete?.let { currentStudent ->
        AlertDialog(
            onDismissRequest = { studentToDelete = null },
            title = { Text("Delete Student?", fontWeight = FontWeight.Bold, color = Red600) },
            text = {
                Text("Are you sure you want to remove ${currentStudent.name} (Roll #${currentStudent.rollNumber})? Their login whitelist access will be revoked.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteStudent(currentStudent.rollNumber)
                        studentToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red600)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { studentToDelete = null }) { Text("Cancel") }
            }
        )
    }
}
