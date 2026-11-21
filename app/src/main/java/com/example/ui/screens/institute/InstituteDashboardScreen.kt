package com.example.ui.screens.institute

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainAppViewModel

/**
 * Requirement 1: Front page interface matching user screenshot:
 * - Header: "Exams", "+ Add New", overflow menu
 * - Filter dropdown: "All" with filter icon
 * - Exam cards: Date block (e.g. Oct 23), title, "Not synced" badge, ? questions, [] scanned count, batch
 * - Bottom Navigation: Exams, Attendance, Classes, More
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstituteDashboardScreen(viewModel: MainAppViewModel) {
    val assessments by viewModel.assessments.collectAsState()
    val batches by viewModel.batches.collectAsState()
    val students by viewModel.students.collectAsState()
    val results by viewModel.scannedResults.collectAsState()
    val activeStaff by viewModel.activeStaff.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Exams, 1: Attendance, 2: Classes, 3: More
    var selectedBatchFilter by remember { mutableStateOf("All") }
    var showFilterDropdown by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val filteredAssessments = remember(assessments, selectedBatchFilter) {
        if (selectedBatchFilter == "All") assessments
        else assessments.filter { it.batchName.equals(selectedBatchFilter, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Exams",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate900,
                            modifier = Modifier.testTag("exams_screen_title")
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // + Add New Button
                            TextButton(
                                onClick = { viewModel.navigateTo(AppScreen.CreateTest) },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF2563EB)),
                                modifier = Modifier.testTag("add_new_exam_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Add New",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = { showMoreMenu = true },
                                    modifier = Modifier.testTag("dashboard_overflow_menu_btn")
                                ) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Slate700)
                                }

                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Staff Roles & Access") },
                                        leadingIcon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.navigateTo(AppScreen.StaffManagement)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Student Directory") },
                                        leadingIcon = { Icon(Icons.Default.People, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.navigateTo(AppScreen.StudentDirectory)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Manage Batches & Classes") },
                                        leadingIcon = { Icon(Icons.Default.School, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.navigateTo(AppScreen.ManageBatches)
                                        }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Switch User / Log Out") },
                                        leadingIcon = { Icon(Icons.Default.Logout, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.navigateTo(AppScreen.RoleSelection)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Filter Bar: "All" dropdown with filter icon
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showFilterDropdown = true }
                                    .testTag("batch_filter_dropdown"),
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Slate300)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = selectedBatchFilter,
                                        fontSize = 14.sp,
                                        color = Slate800,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = "Dropdown",
                                        tint = Slate600
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showFilterDropdown,
                                onDismissRequest = { showFilterDropdown = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All Batches") },
                                    onClick = {
                                        selectedBatchFilter = "All"
                                        showFilterDropdown = false
                                    }
                                )
                                batches.forEach { b ->
                                    DropdownMenuItem(
                                        text = { Text(b.name) },
                                        onClick = {
                                            selectedBatchFilter = b.name
                                            showFilterDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        IconButton(
                            onClick = { showFilterDropdown = true },
                            modifier = Modifier.testTag("filter_icon_btn")
                        ) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = "Filter",
                                tint = Color(0xFF2563EB)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 6.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Assignment, contentDescription = "Exams") },
                    label = { Text("Exams", fontSize = 11.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF2563EB),
                        selectedTextColor = Color(0xFF2563EB),
                        indicatorColor = Color(0xFFEFF6FF)
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        viewModel.navigateTo(AppScreen.StudentDirectory)
                    },
                    icon = { Icon(Icons.Default.HowToReg, contentDescription = "Attendance") },
                    label = { Text("Attendance", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF2563EB),
                        selectedTextColor = Color(0xFF2563EB),
                        indicatorColor = Color(0xFFEFF6FF)
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = {
                        selectedTab = 2
                        viewModel.navigateTo(AppScreen.ManageBatches)
                    },
                    icon = { Icon(Icons.Default.Groups, contentDescription = "Classes") },
                    label = { Text("Classes", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF2563EB),
                        selectedTextColor = Color(0xFF2563EB),
                        indicatorColor = Color(0xFFEFF6FF)
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = {
                        showMoreMenu = true
                    },
                    icon = { Icon(Icons.Default.MoreHoriz, contentDescription = "More") },
                    label = { Text("More", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF2563EB),
                        selectedTextColor = Color(0xFF2563EB),
                        indicatorColor = Color(0xFFEFF6FF)
                    )
                )
            }
        },
        containerColor = Color.White
    ) { paddingValues ->
        if (filteredAssessments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Default.NoteAdd,
                        contentDescription = null,
                        tint = Slate400,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Exams Found",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate800
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap '+ Add New' in the top right to create your first A4 OMR assessment.",
                        fontSize = 13.sp,
                        color = Slate600,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.navigateTo(AppScreen.CreateTest) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create Exam", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredAssessments) { assessment ->
                    val gradedCount = results.count { it.assessmentId == assessment.id }
                    val (monthStr, dayStr) = parseMonthDay(assessment.dateString)

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.navigateTo(AppScreen.ExamDetails(assessment.id))
                            }
                            .testTag("exam_item_${assessment.id}"),
                        color = Color.White
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                // Date Block (Light Blue rounded box like screenshot)
                                Surface(
                                    modifier = Modifier.size(52.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFE0F2FE) // Light Blue 50
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = monthStr,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0284C7)
                                        )
                                        Text(
                                            text = dayStr,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0369A1)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                // Content Column
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = assessment.title,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate900,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Status Pill Badge (e.g. "Not synced" like screenshot)
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (gradedCount > 0) Color(0xFFF0FDF4) else Color(0xFFF1F5F9),
                                            border = BorderStroke(1.dp, if (gradedCount > 0) Color(0xFF86EFAC) else Color(0xFFE2E8F0))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (gradedCount > 0) Icons.Default.Check else Icons.Default.CloudOff,
                                                    contentDescription = null,
                                                    tint = if (gradedCount > 0) Color(0xFF16A34A) else Slate500,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (gradedCount > 0) "$gradedCount graded" else "Not synced",
                                                    fontSize = 11.sp,
                                                    color = if (gradedCount > 0) Color(0xFF16A34A) else Slate600,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Metadata Row: ? totalQuestions | [ ] scannedCount | 👥 batchName
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Questions
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.HelpOutline,
                                                contentDescription = "Questions",
                                                tint = Slate500,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${assessment.totalQuestions}",
                                                fontSize = 13.sp,
                                                color = Slate700,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(text = "|", color = Slate300, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(16.dp))

                                        // Scanned count with frame icon
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.CropFree,
                                                contentDescription = "Scanned",
                                                tint = Slate500,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "$gradedCount",
                                                fontSize = 13.sp,
                                                color = Slate700,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(text = "|", color = Slate300, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(16.dp))

                                        // Batch Name with people icon
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                Icons.Default.Group,
                                                contentDescription = "Batch",
                                                tint = Slate500,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = assessment.batchName,
                                                fontSize = 13.sp,
                                                color = Slate700,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            // Divider between list items
                            HorizontalDivider(
                                color = Color(0xFFF1F5F9),
                                thickness = 1.dp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun parseMonthDay(dateString: String): Pair<String, String> {
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    return try {
        val parts = dateString.split("-", "/")
        if (parts.size >= 3) {
            val monthIdx = (parts[1].toIntOrNull() ?: 10) - 1
            val monthName = months.getOrElse(monthIdx.coerceIn(0, 11)) { "Oct" }
            val day = parts[2].toIntOrNull()?.toString() ?: "23"
            Pair(monthName, day)
        } else {
            Pair("Oct", "23")
        }
    } catch (_: Exception) {
        Pair("Oct", "23")
    }
}
