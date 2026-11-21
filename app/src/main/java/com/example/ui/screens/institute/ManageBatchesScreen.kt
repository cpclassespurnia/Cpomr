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
import com.example.data.model.BatchEntity
import com.example.ui.components.AppTopBar
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainAppViewModel

/**
 * Requirement 2: Batches should be editable and manageable
 */
@Composable
fun ManageBatchesScreen(viewModel: MainAppViewModel) {
    val batches by viewModel.batches.collectAsState()
    val students by viewModel.students.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var batchToEdit by remember { mutableStateOf<BatchEntity?>(null) }
    var batchToDelete by remember { mutableStateOf<BatchEntity?>(null) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Manage Batches & Classes",
                subtitle = "CP CLASSES PURNIA",
                showBackButton = true,
                onBackClick = { viewModel.navigateBack() }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Amber500,
                contentColor = Navy900,
                modifier = Modifier.testTag("add_batch_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Batch")
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
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACADEMIC BATCHES & DIVISIONS (${batches.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate600,
                        letterSpacing = 1.sp
                    )

                    TextButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Navy900)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Batch", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Navy900)
                    }
                }
            }

            items(batches) { batch ->
                val enrolledCount = students.count { it.batchId == batch.id }
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("batch_card_${batch.id}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Slate300),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = batch.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Navy900
                            )
                            Surface(
                                color = Blue100,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = batch.classLevel,
                                    color = Navy900,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = batch.description,
                            fontSize = 12.sp,
                            color = Slate600
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Slate100)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Enrolled: $enrolledCount  •  Session: ${batch.academicYear}",
                                fontSize = 11.sp,
                                color = Slate500
                            )

                            // Edit and Delete Actions
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = { batchToEdit = batch },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Batch", tint = Navy700, modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = { batchToDelete = batch },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Batch", tint = Red500, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Batch Dialog
    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var classLevel by remember { mutableStateOf("Class 12") }
        var academicYear by remember { mutableStateOf("2026-2027") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Custom Batch / Class", fontWeight = FontWeight.Bold, color = Navy900) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Batch Name (e.g. 12th CP 26-27)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description / Target") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = classLevel,
                        onValueChange = { classLevel = it },
                        label = { Text("Class Level (e.g. Class 11, Class 12, Dropper)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = academicYear,
                        onValueChange = { academicYear = it },
                        label = { Text("Academic Session") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            viewModel.addNewBatch(name, description, classLevel, academicYear)
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Navy900)
                ) {
                    Text("Save Batch", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Edit Batch Dialog (Requirement 2)
    batchToEdit?.let { currentBatch ->
        var editName by remember(currentBatch) { mutableStateOf(currentBatch.name) }
        var editDescription by remember(currentBatch) { mutableStateOf(currentBatch.description) }
        var editClassLevel by remember(currentBatch) { mutableStateOf(currentBatch.classLevel) }
        var editYear by remember(currentBatch) { mutableStateOf(currentBatch.academicYear) }

        AlertDialog(
            onDismissRequest = { batchToEdit = null },
            title = { Text("Edit Batch", fontWeight = FontWeight.Bold, color = Navy900) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Batch Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = { editDescription = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editClassLevel,
                        onValueChange = { editClassLevel = it },
                        label = { Text("Class Level") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editYear,
                        onValueChange = { editYear = it },
                        label = { Text("Academic Session") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isNotBlank()) {
                            viewModel.updateBatch(
                                currentBatch.copy(
                                    name = editName,
                                    description = editDescription,
                                    classLevel = editClassLevel,
                                    academicYear = editYear
                                )
                            )
                            batchToEdit = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Navy900)
                ) {
                    Text("Update Batch", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { batchToEdit = null }) { Text("Cancel") }
            }
        )
    }

    // Delete Batch Dialog
    batchToDelete?.let { currentBatch ->
        AlertDialog(
            onDismissRequest = { batchToDelete = null },
            title = { Text("Delete Batch?", fontWeight = FontWeight.Bold, color = Red600) },
            text = {
                Text("Are you sure you want to delete batch '${currentBatch.name}'? Enrolled students will need to be reassigned.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteBatch(currentBatch.id)
                        batchToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red600)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { batchToDelete = null }) { Text("Cancel") }
            }
        )
    }
}
