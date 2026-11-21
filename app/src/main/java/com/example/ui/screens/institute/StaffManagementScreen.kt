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
import com.example.data.model.StaffEntity
import com.example.ui.components.AppTopBar
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainAppViewModel

@Composable
fun StaffManagementScreen(viewModel: MainAppViewModel) {
    val staffList by viewModel.staffMembers.collectAsState()
    val activeStaff by viewModel.activeStaff.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var staffToEdit by remember { mutableStateOf<StaffEntity?>(null) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Staff Roles & Permissions",
                subtitle = "INSTITUTE ACCESS CONTROL",
                showBackButton = true,
                onBackClick = { viewModel.navigateBack() }
            )
        },
        floatingActionButton = {
            if (activeStaff?.canManageStudentsAndBatches == true) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = Amber500,
                    contentColor = Navy900,
                    modifier = Modifier.testTag("add_staff_fab")
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Add Staff")
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                // Active Logged-In Staff Indicator Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Navy900),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Amber500),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = Navy900)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Current Session: ${activeStaff?.name ?: "Admin"}",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${activeStaff?.role ?: "Admin"} • ${activeStaff?.email}",
                                    color = Amber500,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Active Permissions for this device:",
                            color = Slate300,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PermissionBadge(label = "Scan OMR", granted = activeStaff?.canScanOmr == true)
                            PermissionBadge(label = "Edit Key", granted = activeStaff?.canEditAnswerKeys == true)
                            PermissionBadge(label = "Create Test", granted = activeStaff?.canCreateAssessments == true)
                            PermissionBadge(label = "Manage", granted = activeStaff?.canManageStudentsAndBatches == true)
                        }
                    }
                }
            }

            item {
                Text(
                    text = "REGISTERED INSTITUTE STAFF (${staffList.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate600,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(staffList) { staff ->
                val isCurrent = staff.id == activeStaff?.id
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(if (isCurrent) 1.5.dp else 1.dp, if (isCurrent) Amber500 else Slate300),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isCurrent) Amber100 else Blue100),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = staff.name.take(2).uppercase(),
                                    color = if (isCurrent) Amber600 else Navy700,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = staff.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Navy900
                                    )
                                    if (isCurrent) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = Amber500,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "ACTIVE",
                                                color = Navy900,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${staff.email} • ${staff.role}",
                                    fontSize = 12.sp,
                                    color = Slate600
                                )
                            }

                            // Switch to this staff button
                            if (!isCurrent) {
                                TextButton(
                                    onClick = { viewModel.setActiveStaff(staff) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Blue500)
                                ) {
                                    Text("Switch", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Divider(color = Slate100)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Permission chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PermissionBadge(label = "Scan OMR", granted = staff.canScanOmr)
                            PermissionBadge(label = "Edit Keys", granted = staff.canEditAnswerKeys)
                            PermissionBadge(label = "Create Tests", granted = staff.canCreateAssessments)
                            PermissionBadge(label = "Manage DB", granted = staff.canManageStudentsAndBatches)
                            PermissionBadge(label = "Delete", granted = staff.canDeleteResults)
                        }

                        // Edit / Delete buttons if admin
                        if (activeStaff?.canManageStudentsAndBatches == true && !isCurrent) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { staffToEdit = staff }) {
                                    Text("Edit Permissions", fontSize = 12.sp, color = Navy700)
                                }
                                TextButton(onClick = { viewModel.deleteStaff(staff.id) }) {
                                    Text("Remove", fontSize = 12.sp, color = Red600)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add or Edit Staff Dialog (Requirement 5)
    val editing = staffToEdit
    if (showAddDialog || editing != null) {
        var name by remember { mutableStateOf(editing?.name ?: "") }
        var email by remember { mutableStateOf(editing?.email ?: "") }
        var role by remember { mutableStateOf(editing?.role ?: "Evaluator / Teacher") }

        var canScan by remember { mutableStateOf(editing?.canScanOmr ?: true) }
        var canEditKeys by remember { mutableStateOf(editing?.canEditAnswerKeys ?: true) }
        var canCreateTests by remember { mutableStateOf(editing?.canCreateAssessments ?: true) }
        var canManage by remember { mutableStateOf(editing?.canManageStudentsAndBatches ?: false) }
        var canSendReports by remember { mutableStateOf(editing?.canSendReports ?: true) }
        var canDelete by remember { mutableStateOf(editing?.canDeleteResults ?: false) }

        val roles = listOf("Institute Admin", "Evaluator / Teacher", "Scanner Staff", "Data Entry Operator")

        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                staffToEdit = null
            },
            title = {
                Text(
                    text = if (editing != null) "Edit Staff Permissions" else "Add New Staff Member",
                    fontWeight = FontWeight.Bold,
                    color = Navy900
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Staff Full Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Device Gmail ID") },
                        placeholder = { Text("teacher@gmail.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Assign Role:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate700)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        roles.take(2).forEach { r ->
                            FilterChip(
                                selected = role == r,
                                onClick = {
                                    role = r
                                    if (r == "Institute Admin") {
                                        canScan = true; canEditKeys = true; canCreateTests = true
                                        canManage = true; canSendReports = true; canDelete = true
                                    } else if (r == "Evaluator / Teacher") {
                                        canScan = true; canEditKeys = true; canCreateTests = true
                                        canManage = false; canSendReports = true; canDelete = false
                                    }
                                },
                                label = { Text(r.substringBefore(" "), fontSize = 11.sp) }
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        roles.drop(2).forEach { r ->
                            FilterChip(
                                selected = role == r,
                                onClick = {
                                    role = r
                                    if (r == "Scanner Staff") {
                                        canScan = true; canEditKeys = false; canCreateTests = false
                                        canManage = false; canSendReports = false; canDelete = false
                                    }
                                },
                                label = { Text(r.substringBefore(" "), fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Fine-Grained Permissions:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate700)

                    PermissionCheckbox("Can Scan Physical OMR Sheets", canScan) { canScan = it }
                    PermissionCheckbox("Can Edit & Assign Answer Keys", canEditKeys) { canEditKeys = it }
                    PermissionCheckbox("Can Create Assessments & Generate OMR", canCreateTests) { canCreateTests = it }
                    PermissionCheckbox("Can Manage Students, Batches & Staff", canManage) { canManage = it }
                    PermissionCheckbox("Can Send WhatsApp/Email Report Cards", canSendReports) { canSendReports = it }
                    PermissionCheckbox("Can Delete Results & Assessments", canDelete) { canDelete = it }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editing != null) {
                            viewModel.updateStaff(
                                editing.copy(
                                    name = name,
                                    email = email.trim().lowercase(),
                                    role = role,
                                    canScanOmr = canScan,
                                    canEditAnswerKeys = canEditKeys,
                                    canCreateAssessments = canCreateTests,
                                    canManageStudentsAndBatches = canManage,
                                    canSendReports = canSendReports,
                                    canDeleteResults = canDelete
                                )
                            )
                        } else {
                            viewModel.addNewStaff(
                                name = name,
                                email = email,
                                role = role,
                                permissions = mapOf(
                                    "canScanOmr" to canScan,
                                    "canEditAnswerKeys" to canEditKeys,
                                    "canCreateAssessments" to canCreateTests,
                                    "canManageStudentsAndBatches" to canManage,
                                    "canSendReports" to canSendReports,
                                    "canDeleteResults" to canDelete
                                )
                            )
                        }
                        showAddDialog = false
                        staffToEdit = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Navy900),
                    enabled = name.isNotBlank() && email.isNotBlank()
                ) {
                    Text("Save Staff")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    staffToEdit = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PermissionBadge(label: String, granted: Boolean) {
    Surface(
        color = if (granted) Green100 else Slate100,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(0.5.dp, if (granted) Green500 else Slate300)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (granted) Green600 else Slate500,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun PermissionCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, fontSize = 12.sp, color = Slate800)
    }
}
