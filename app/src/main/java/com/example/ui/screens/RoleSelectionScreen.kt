package com.example.ui.screens

import android.accounts.AccountManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainAppViewModel

@Composable
fun RoleSelectionScreen(viewModel: MainAppViewModel) {
    val context = LocalContext.current
    val staffList by viewModel.staffMembers.collectAsState()
    val students by viewModel.students.collectAsState()
    val loginError by viewModel.loginError.collectAsState()

    var showStaffGmailDialog by remember { mutableStateOf(false) }
    var showStudentGmailDialog by remember { mutableStateOf(false) }
    var selectedStaffGmail by remember { mutableStateOf("") }
    var studentGmailInput by remember { mutableStateOf("") }

    // Detect Google Accounts on device if available
    val deviceGoogleAccounts = remember {
        try {
            val am = AccountManager.get(context)
            am.getAccountsByType("com.google").map { it.name }
        } catch (_: Exception) {
            emptyList()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("role_selection_screen"),
        color = Slate50
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Hero Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Navy900)
                    .padding(top = 48.dp, bottom = 32.dp, start = 24.dp, end = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Amber500),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "CP",
                            color = Navy900,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "CP CLASSES PURNIA",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = "Smart OMR Evaluation & Student Performance Portal",
                        color = Amber500,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Navy800,
                        border = BorderStroke(1.dp, Blue500.copy(alpha = 0.4f)),
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Text(
                            text = "ISO 216 Standard A4  •  1–200 Questions  •  Topic Wise & Optional",
                            color = Blue100,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Error banner if any
            if (loginError != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Red100),
                    border = BorderStroke(1.dp, Red500)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = Red600)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = loginError ?: "",
                            color = Red600,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearLoginError() }) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Red600)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "SELECT YOUR LOGIN PORTAL",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Slate600,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Card 1: Institute Staff Login (with Device Gmail & Roles)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .testTag("institute_login_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                border = BorderStroke(1.dp, Slate300)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Navy100 ?: Blue100),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = Navy900,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Institute & Staff Portal",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Navy900
                            )
                            Text(
                                text = "Admin, Evaluator, Scanner Staff",
                                fontSize = 12.sp,
                                color = Slate500
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Generate A4 OMR sheets (1–200 questions), assign answer keys, manage batches, configure staff roles & permissions, and scan OMR sheets.",
                        fontSize = 13.sp,
                        color = Slate600,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showStaffGmailDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("institute_gmail_login_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = Navy900),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null, tint = Amber500)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Login with Device Gmail",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Card 2: Student Login (Whitelisted Gmail Verification)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .testTag("student_login_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                border = BorderStroke(1.dp, Slate300)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Amber100),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.School,
                                contentDescription = null,
                                tint = Amber600,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Student Scorecard Portal",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Navy900
                            )
                            Text(
                                text = "Whitelisted Gmail Login",
                                fontSize = 12.sp,
                                color = Amber600,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Students can log in with their registered Gmail address to view topic-wise scorecards, ranks, topper marks, and download official report cards.",
                        fontSize = 13.sp,
                        color = Slate600,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            viewModel.clearLoginError()
                            showStudentGmailDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_gmail_login_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = Amber500),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Navy900)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Student Gmail Login",
                            color = Navy900,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { viewModel.navigateTo(AppScreen.StudentLogin) },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = "Or enter Roll Number directly →",
                            fontSize = 12.sp,
                            color = Slate600
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // Dialog: Staff / Institute Gmail Login (Requirement 3 & 5)
    if (showStaffGmailDialog) {
        AlertDialog(
            onDismissRequest = { showStaffGmailDialog = false },
            title = {
                Text(
                    text = "Institute Staff Login",
                    fontWeight = FontWeight.Bold,
                    color = Navy900
                )
            },
            text = {
                Column {
                    Text(
                        text = "Select your logged-in Google / Gmail account or choose a registered staff role:",
                        fontSize = 13.sp,
                        color = Slate600
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Device Gmail account option
                    val defaultDeviceEmail = deviceGoogleAccounts.firstOrNull() ?: "mdsalikalam@gmail.com"
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Blue50),
                        border = BorderStroke(1.dp, Blue500)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Navy900)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = defaultDeviceEmail,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Navy900
                                )
                                Text(
                                    text = "Device Logged-in Google Account",
                                    fontSize = 11.sp,
                                    color = Slate600
                                )
                            }
                            Button(
                                onClick = {
                                    showStaffGmailDialog = false
                                    viewModel.loginInstituteWithGmail(defaultDeviceEmail)
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Navy900)
                            ) {
                                Text("Login", fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Or switch to registered staff profile:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate700
                    )

                    staffList.forEach { staff ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = staff.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Navy900
                                )
                                Text(
                                    text = "${staff.email} • ${staff.role}",
                                    fontSize = 11.sp,
                                    color = Slate500
                                )
                            }
                            TextButton(
                                onClick = {
                                    showStaffGmailDialog = false
                                    viewModel.setActiveStaff(staff)
                                    viewModel.navigateTo(AppScreen.InstituteDashboard)
                                }
                            ) {
                                Text("Select", fontSize = 12.sp, color = Blue500)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showStaffGmailDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Student Gmail Login (Requirement 3 & 4)
    if (showStudentGmailDialog) {
        val defaultStudentEmail = deviceGoogleAccounts.firstOrNull() ?: "mdsalikalam@gmail.com"
        AlertDialog(
            onDismissRequest = { showStudentGmailDialog = false },
            title = {
                Text(
                    text = "Student Gmail Login",
                    fontWeight = FontWeight.Bold,
                    color = Navy900
                )
            },
            text = {
                Column {
                    Text(
                        text = "Students can ONLY log in using Gmail addresses registered in CP Classes database.",
                        fontSize = 12.sp,
                        color = Slate600
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Quick option 1: Use currently logged-in device Gmail
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Amber100),
                        border = BorderStroke(1.dp, Amber500)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, tint = Amber600)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = defaultStudentEmail,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Navy900
                                )
                                Text(
                                    text = "Device Gmail (Aarav Sharma - CP25-01)",
                                    fontSize = 11.sp,
                                    color = Slate600
                                )
                            }
                            Button(
                                onClick = {
                                    showStudentGmailDialog = false
                                    viewModel.loginStudentWithGmail(defaultStudentEmail)
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Amber600)
                            ) {
                                Text("Verify & Login", fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Or type another student Gmail address:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Slate700
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = studentGmailInput,
                        onValueChange = { studentGmailInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_gmail_input"),
                        placeholder = { Text("e.g. priya.verma@gmail.com", fontSize = 13.sp) },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Whitelisted Student Emails in DB:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate500
                    )
                    students.take(3).forEach { s ->
                        Text(
                            text = "• ${s.name}: ${s.email} (Roll: ${s.rollNumber})",
                            fontSize = 11.sp,
                            color = Slate600
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (studentGmailInput.isNotBlank()) {
                            showStudentGmailDialog = false
                            viewModel.loginStudentWithGmail(studentGmailInput)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Navy900),
                    enabled = studentGmailInput.isNotBlank()
                ) {
                    Text("Check DB & Login")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStudentGmailDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private val Navy100: Color? = Color(0xFFE2E8F0)
