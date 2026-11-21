package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.screens.RoleSelectionScreen
import com.example.ui.screens.ScoreReportDetailScreen
import com.example.ui.screens.institute.*
import com.example.ui.screens.student.StudentDashboardScreen
import com.example.ui.screens.student.StudentLoginScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainAppViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainAppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: MainAppViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()

    // Global back handling for state-based navigation
    BackHandler(enabled = currentScreen !is AppScreen.RoleSelection) {
        val handled = viewModel.navigateBack()
        if (!handled) {
            viewModel.navigateTo(AppScreen.RoleSelection)
        }
    }

    when (val screen = currentScreen) {
        is AppScreen.RoleSelection -> {
            RoleSelectionScreen(viewModel = viewModel)
        }
        is AppScreen.InstituteDashboard -> {
            InstituteDashboardScreen(viewModel = viewModel)
        }
        is AppScreen.CreateTest -> {
            CreateTestScreen(viewModel = viewModel)
        }
        is AppScreen.ExamDetails -> {
            ExamDetailsScreen(viewModel = viewModel, assessmentId = screen.assessmentId)
        }
        is AppScreen.AnswerKeyEditor -> {
            AnswerKeyEditorScreen(
                viewModel = viewModel,
                assessmentId = screen.assessmentId,
                initialSetCode = screen.setCode
            )
        }
        is AppScreen.OmrScanner -> {
            OmrScannerScreen(viewModel = viewModel, assessmentId = screen.assessmentId)
        }
        is AppScreen.Camera -> {
            com.example.ui.screens.CameraScreen(
                viewModel = viewModel,
                assessmentId = screen.assessmentId,
                onClose = { viewModel.navigateBack() }
            )
        }
        is AppScreen.ScanVerification -> {
            ScanVerificationScreen(
                viewModel = viewModel,
                assessmentId = screen.assessmentId,
                roll = screen.roll,
                setCode = screen.setCode,
                markedAnswers = screen.markedAnswers
            )
        }
        is AppScreen.BatchLeaderboard -> {
            BatchLeaderboardScreen(viewModel = viewModel, assessmentId = screen.assessmentId)
        }
        is AppScreen.ItemAnalysis -> {
            ItemAnalysisScreen(viewModel = viewModel, assessmentId = screen.assessmentId)
        }
        is AppScreen.OmrTemplateViewer -> {
            OmrTemplateViewerScreen(viewModel = viewModel, assessmentId = screen.assessmentId)
        }
        is AppScreen.ManageBatches -> {
            ManageBatchesScreen(viewModel = viewModel)
        }
        is AppScreen.StudentDirectory -> {
            StudentDirectoryScreen(viewModel = viewModel)
        }
        is AppScreen.StaffManagement -> {
            StaffManagementScreen(viewModel = viewModel)
        }
        is AppScreen.StudentLogin -> {
            StudentLoginScreen(viewModel = viewModel)
        }
        is AppScreen.StudentDashboard -> {
            StudentDashboardScreen(viewModel = viewModel, rollNumber = screen.rollNumber)
        }
        is AppScreen.ScoreReportDetail -> {
            ScoreReportDetailScreen(
                viewModel = viewModel,
                resultId = screen.resultId,
                fromStudentView = screen.fromStudentView
            )
        }
    }
}
