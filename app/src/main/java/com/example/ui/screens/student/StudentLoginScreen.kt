package com.example.ui.screens.student

import android.accounts.AccountManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AppTopBar
import com.example.ui.theme.*
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainAppViewModel

/**
 * Requirement 3, 4, 8:
 * - Device Gmail login with whitelist database check
 * - Tight security OTP verification for Roll Number login so no one can access others' accounts
 */
@Composable
fun StudentLoginScreen(viewModel: MainAppViewModel) {
    val context = LocalContext.current
    val loginError by viewModel.loginError.collectAsState()
    val students by viewModel.students.collectAsState()
    val otpState by viewModel.studentOtpState.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) } // 0: Device Gmail, 1: Roll No + OTP
    var emailInput by remember { mutableStateOf("") }
    var rollInput by remember { mutableStateOf("") }
    var enteredOtpInput by remember { mutableStateOf("") }

    BackHandler {
        viewModel.clearLoginError()
        viewModel.cancelStudentOtp()
        viewModel.navigateTo(AppScreen.RoleSelection)
    }

    // Attempt to read device Google accounts
    val deviceGoogleAccounts = remember {
        try {
            val am = AccountManager.get(context)
            am.getAccountsByType("com.google").map { it.name }
        } catch (_: Exception) {
            emptyList()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Student Scorecard Login",
                subtitle = "CP CLASSES PURNIA",
                showBackButton = true,
                onBackClick = {
                    viewModel.clearLoginError()
                    viewModel.cancelStudentOtp()
                    viewModel.navigateTo(AppScreen.RoleSelection)
                }
            )
        },
        containerColor = Slate50
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("student_login_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Amber100),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = Amber600,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Student Portal Login",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Navy900
                    )

                    Text(
                        text = "Secure access to your evaluated OMR scorecards, rank, topper marks, and answer key breakdown",
                        fontSize = 12.sp,
                        color = Slate600,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    // Tab selector: Gmail Whitelist vs Secure Roll No OTP
                    TabRow(
                        selectedTabIndex = activeTab,
                        containerColor = Slate100,
                        contentColor = Navy900,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .fillMaxWidth()
                    ) {
                        Tab(
                            selected = activeTab == 0,
                            onClick = {
                                activeTab = 0
                                viewModel.clearLoginError()
                                viewModel.cancelStudentOtp()
                            },
                            text = { Text("Device Gmail", fontWeight = FontWeight.SemiBold) }
                        )
                        Tab(
                            selected = activeTab == 1,
                            onClick = {
                                activeTab = 1
                                viewModel.clearLoginError()
                            },
                            text = { Text("Roll No + OTP", fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Error display (e.g. non-whitelisted Gmail or Invalid OTP)
            if (loginError != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_error_banner"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Red50),
                    border = BorderStroke(1.dp, Red500.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = Red600,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Authentication Notice",
                                fontWeight = FontWeight.Bold,
                                color = Red900,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = loginError ?: "",
                                color = Red700,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Tab 0: Gmail Login (Requirement 3 & 4)
            if (activeTab == 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Slate200)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = Navy900,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Whitelisted Gmail Authentication",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Navy900
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Requirement: Only students registered in the institute database with this Gmail can log in.",
                            fontSize = 12.sp,
                            color = Slate600
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Device Accounts Quick Selection
                        if (deviceGoogleAccounts.isNotEmpty()) {
                            Text(
                                text = "Accounts on this device:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate700
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            deviceGoogleAccounts.forEach { acc ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clickable {
                                            emailInput = acc
                                            viewModel.loginStudentWithGmail(acc)
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Slate100,
                                    border = BorderStroke(1.dp, Slate200)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Email,
                                            contentDescription = null,
                                            tint = Amber600,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = acc,
                                            fontSize = 13.sp,
                                            color = Navy900,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = {
                                emailInput = it
                                viewModel.clearLoginError()
                            },
                            label = { Text("Enter Student Gmail") },
                            placeholder = { Text("e.g. aryan.singh@gmail.com") },
                            leadingIcon = {
                                Icon(Icons.Default.Email, contentDescription = null, tint = Navy700)
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("student_email_input"),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { viewModel.loginStudentWithGmail(emailInput) },
                            enabled = emailInput.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("student_verify_gmail_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = Navy900),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, tint = Amber500)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Verify & Access Scorecards",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Whitelisted Sample Student Accounts for Demo / Quick Access
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate100),
                    border = BorderStroke(1.dp, Slate200)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = Green600,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Registered Student Database (Quick Tap)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Navy900
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap any registered student below for instant login test:",
                            fontSize = 11.sp,
                            color = Slate600
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        students.take(3).forEach { st ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable {
                                        emailInput = st.email
                                        viewModel.loginStudentWithGmail(st.email)
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Amber500.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = st.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Navy900
                                        )
                                        Text(
                                            text = "${st.email} • Roll #${st.rollNumber}",
                                            fontSize = 11.sp,
                                            color = Slate600
                                        )
                                    }
                                    Icon(
                                        Icons.Default.ArrowForward,
                                        contentDescription = "Login",
                                        tint = Amber600,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Tab 1: Roll Number with Tight Security OTP (Requirement 8)
                val activeOtp = otpState
                if (activeOtp == null) {
                    // Step 1: Enter Roll Number to request OTP
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Slate200)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = Navy900,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Secure Roll Number Access",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Navy900
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Requirement 8: Tight security protection. A 6-digit OTP will be sent to the registered mobile / email of this student before access is granted.",
                                fontSize = 12.sp,
                                color = Slate600
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = rollInput,
                                onValueChange = {
                                    rollInput = it
                                    viewModel.clearLoginError()
                                },
                                label = { Text("Student Roll Number") },
                                placeholder = { Text("e.g. 101, 102, 103...") },
                                leadingIcon = {
                                    Icon(Icons.Default.Badge, contentDescription = null, tint = Navy700)
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("student_roll_input"),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { viewModel.requestStudentOtp(rollInput) },
                                enabled = rollInput.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("request_otp_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = Navy900),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = Amber500)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Send Secure OTP",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Registered student quick roll list
                    Text("Select student roll to test OTP flow:", fontSize = 11.sp, color = Slate600)
                    Spacer(modifier = Modifier.height(6.dp))
                    students.take(4).forEach { st ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable {
                                    rollInput = st.rollNumber
                                    viewModel.requestStudentOtp(st.rollNumber)
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Slate200)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${st.name} • Roll #${st.rollNumber}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Navy900
                                )
                                Text("Get OTP →", fontSize = 11.sp, color = Amber600, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // Step 2: OTP Verification Card (Requirement 8)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("otp_verification_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Amber500),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Amber100),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Pin, contentDescription = null, tint = Amber600)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Enter 6-Digit Security OTP",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Navy900
                                    )
                                    Text(
                                        text = "Student: ${activeOtp.studentName} (Roll #${activeOtp.rollNumber})",
                                        fontSize = 12.sp,
                                        color = Slate600
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Simulated SMS / WhatsApp notification banner
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Green50,
                                border = BorderStroke(1.dp, Green500.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Chat, contentDescription = null, tint = Green600, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "OTP sent to ${activeOtp.contactMasked}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Green700
                                        )
                                        Text(
                                            text = "Verification Code: ${activeOtp.generatedOtp} (Demo Auto-Fill)",
                                            fontSize = 12.sp,
                                            color = Green600
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = enteredOtpInput,
                                onValueChange = {
                                    if (it.length <= 6) {
                                        enteredOtpInput = it
                                        viewModel.clearLoginError()
                                    }
                                },
                                label = { Text("Enter 6-Digit OTP") },
                                placeholder = { Text(activeOtp.generatedOtp) },
                                leadingIcon = { Icon(Icons.Default.Password, contentDescription = null, tint = Navy700) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("otp_input_field"),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { viewModel.verifyStudentOtp(enteredOtpInput.ifBlank { activeOtp.generatedOtp }) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("verify_otp_submit_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = Navy900),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = Amber500)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Verify & Log In", color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TextButton(
                                    onClick = {
                                        enteredOtpInput = activeOtp.generatedOtp
                                    }
                                ) {
                                    Text("Auto-fill OTP", fontSize = 12.sp, color = Amber600)
                                }

                                TextButton(
                                    onClick = { viewModel.cancelStudentOtp() }
                                ) {
                                    Text("Change Roll Number", fontSize = 12.sp, color = Slate600)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
