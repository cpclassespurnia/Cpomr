package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.*
import com.example.data.repository.AssessmentRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

sealed class AppScreen {
    data object RoleSelection : AppScreen()
    data object InstituteDashboard : AppScreen()
    data object CreateTest : AppScreen()
    data class ExamDetails(val assessmentId: Long) : AppScreen()
    data class AnswerKeyEditor(val assessmentId: Long, val setCode: String = "A") : AppScreen()
    data class OmrScanner(val assessmentId: Long) : AppScreen()
    data class Camera(val assessmentId: Long? = null) : AppScreen()
    data class ScanVerification(
        val assessmentId: Long,
        val roll: String,
        val setCode: String,
        val markedAnswers: List<String>
    ) : AppScreen()
    data class BatchLeaderboard(val assessmentId: Long) : AppScreen()
    data class ItemAnalysis(val assessmentId: Long) : AppScreen()
    data class OmrTemplateViewer(val assessmentId: Long) : AppScreen()
    data object ManageBatches : AppScreen()
    data object StudentDirectory : AppScreen()
    data object StaffManagement : AppScreen()
    data object StudentLogin : AppScreen()
    data class StudentDashboard(val rollNumber: String) : AppScreen()
    data class ScoreReportDetail(val resultId: Long, val fromStudentView: Boolean = false) : AppScreen()
}

data class StudentOtpState(
    val rollNumber: String,
    val studentName: String,
    val contactMasked: String,
    val generatedOtp: String,
    val student: StudentEntity
)

class MainAppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AssessmentRepository(AppDatabase.getDatabase(application))

    private val _currentScreen = MutableStateFlow<AppScreen>(AppScreen.RoleSelection)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val screenBackStack = mutableListOf<AppScreen>()

    val assessments: StateFlow<List<AssessmentEntity>> = repository.allAssessments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val batches: StateFlow<List<BatchEntity>> = repository.allBatches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val students: StateFlow<List<StudentEntity>> = repository.allStudents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val staffMembers: StateFlow<List<StaffEntity>> = repository.allStaff
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scannedResults: StateFlow<List<ScannedResultEntity>> = repository.allResults
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active logged-in staff and student states
    private val _activeStaff = MutableStateFlow<StaffEntity?>(null)
    val activeStaff: StateFlow<StaffEntity?> = _activeStaff.asStateFlow()

    private val _activeStudent = MutableStateFlow<StudentEntity?>(null)
    val activeStudent: StateFlow<StudentEntity?> = _activeStudent.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    // Requirement 8: OTP security state for student login
    private val _studentOtpState = MutableStateFlow<StudentOtpState?>(null)
    val studentOtpState: StateFlow<StudentOtpState?> = _studentOtpState.asStateFlow()

    // Temporary storage for answer key editing on generated OMR sheet
    private val _editingAnswerKey = MutableStateFlow<List<String>>(emptyList())
    val editingAnswerKey: StateFlow<List<String>> = _editingAnswerKey.asStateFlow()

    private val _editingSetCode = MutableStateFlow("A")
    val editingSetCode: StateFlow<String> = _editingSetCode.asStateFlow()

    init {
        // Initialize default active staff
        viewModelScope.launch {
            repository.allStaff.collect { list ->
                if (_activeStaff.value == null && list.isNotEmpty()) {
                    _activeStaff.value = list.firstOrNull { it.role == "Institute Admin" } ?: list.first()
                }
            }
        }
    }

    fun navigateTo(screen: AppScreen) {
        screenBackStack.add(_currentScreen.value)
        _currentScreen.value = screen
    }

    fun navigateBack(): Boolean {
        if (screenBackStack.isNotEmpty()) {
            val prev = screenBackStack.removeAt(screenBackStack.size - 1)
            _currentScreen.value = prev
            return true
        }
        return false
    }

    fun clearLoginError() {
        _loginError.value = null
    }

    // Requirement 3 & 4: Device Gmail Student Login with Database Whitelist Validation
    fun loginStudentWithGmail(gmail: String) {
        val trimmedEmail = gmail.trim().lowercase()
        viewModelScope.launch {
            _loginError.value = null
            val student = repository.getStudentByEmail(trimmedEmail)
            if (student != null) {
                _activeStudent.value = student
                navigateTo(AppScreen.StudentDashboard(student.rollNumber))
            } else {
                _loginError.value = "Access Denied: Gmail account '$trimmedEmail' is not registered in the CP Classes database. Please contact institute administration."
            }
        }
    }

    // Requirement 8: Tight security OTP login for Roll Numbers
    fun requestStudentOtp(rollNumber: String) {
        viewModelScope.launch {
            _loginError.value = null
            val student = repository.getStudentByRoll(rollNumber.trim())
            if (student != null) {
                val otp = (100000 + (Math.random() * 900000).toInt()).toString()
                val rawContact = student.whatsapp.ifBlank { student.phone.ifBlank { student.email } }
                val masked = if (rawContact.contains("@")) {
                    val user = rawContact.substringBefore("@")
                    val domain = rawContact.substringAfter("@")
                    "${user.take(2)}***@$domain"
                } else {
                    val clean = rawContact.replace(Regex("[^0-9]"), "")
                    if (clean.length >= 10) "+91 ${clean.take(2)}*** ***${clean.takeLast(2)}"
                    else "Registered Mobile"
                }
                _studentOtpState.value = StudentOtpState(
                    rollNumber = student.rollNumber,
                    studentName = student.name,
                    contactMasked = masked,
                    generatedOtp = otp,
                    student = student
                )
            } else {
                _loginError.value = "Roll Number '$rollNumber' not found in CP Classes records."
            }
        }
    }

    fun verifyStudentOtp(enteredOtp: String) {
        val state = _studentOtpState.value
        if (state == null) {
            _loginError.value = "Session expired. Please request a new OTP."
            return
        }
        if (enteredOtp.trim() == state.generatedOtp) {
            _activeStudent.value = state.student
            _loginError.value = null
            _studentOtpState.value = null
            navigateTo(AppScreen.StudentDashboard(state.student.rollNumber))
        } else {
            _loginError.value = "Incorrect OTP code. Please enter the valid 6-digit code sent to ${state.contactMasked}."
        }
    }

    fun cancelStudentOtp() {
        _studentOtpState.value = null
        _loginError.value = null
    }

    fun loginStudentByRoll(rollNumber: String) {
        requestStudentOtp(rollNumber)
    }

    // Requirement 3 & 5: Device Gmail Institute Staff Login with Role-Based Permissions
    fun loginInstituteWithGmail(gmail: String) {
        val trimmedEmail = gmail.trim().lowercase()
        viewModelScope.launch {
            val staff = repository.getStaffByEmail(trimmedEmail)
            if (staff != null) {
                _activeStaff.value = staff
            } else {
                // If new email, register as Admin or default staff
                val defaultAdmin = StaffEntity(
                    name = "Institute Staff (${trimmedEmail.substringBefore("@")})",
                    email = trimmedEmail,
                    role = "Institute Admin",
                    canScanOmr = true,
                    canEditAnswerKeys = true,
                    canCreateAssessments = true,
                    canManageStudentsAndBatches = true,
                    canSendReports = true,
                    canDeleteResults = true
                )
                val id = repository.addNewStaff(defaultAdmin)
                _activeStaff.value = defaultAdmin.copy(id = id)
            }
            navigateTo(AppScreen.InstituteDashboard)
        }
    }

    fun setActiveStaff(staff: StaffEntity) {
        _activeStaff.value = staff
    }

    // Staff Management (Requirement 5)
    fun addNewStaff(name: String, email: String, role: String, permissions: Map<String, Boolean>) {
        viewModelScope.launch {
            val staff = StaffEntity(
                name = name,
                email = email.trim().lowercase(),
                role = role,
                canScanOmr = permissions["canScanOmr"] ?: true,
                canEditAnswerKeys = permissions["canEditAnswerKeys"] ?: true,
                canCreateAssessments = permissions["canCreateAssessments"] ?: true,
                canManageStudentsAndBatches = permissions["canManageStudentsAndBatches"] ?: false,
                canSendReports = permissions["canSendReports"] ?: true,
                canDeleteResults = permissions["canDeleteResults"] ?: false
            )
            repository.addNewStaff(staff)
        }
    }

    fun updateStaff(staff: StaffEntity) {
        viewModelScope.launch {
            repository.updateStaff(staff)
            if (_activeStaff.value?.id == staff.id) {
                _activeStaff.value = staff
            }
        }
    }

    fun deleteStaff(staffId: Long) {
        viewModelScope.launch {
            repository.deleteStaff(staffId)
        }
    }

    // Requirement 6: Create test without requiring upfront answer key
    fun createAssessmentWithoutKey(
        title: String,
        batchId: Long,
        batchName: String,
        subject: String,
        totalQuestions: Int,
        marksPerCorrect: Float,
        negativeMarksPerWrong: Float,
        marksUnattempted: Float,
        rollDigits: Int,
        dateString: String,
        includeNameField: Boolean,
        includeRollField: Boolean,
        includeDateField: Boolean,
        includeBatchField: Boolean,
        includePhoneField: Boolean,
        topics: List<TopicDefinition>,
        optionalRulesDescription: String
    ) {
        viewModelScope.launch {
            val topicsJson = serializeTopics(topics)
            val assessment = AssessmentEntity(
                title = title,
                batchId = batchId,
                batchName = batchName,
                subject = subject,
                totalQuestions = totalQuestions.coerceIn(1, 200),
                marksPerCorrect = marksPerCorrect,
                negativeMarksPerWrong = negativeMarksPerWrong,
                marksUnattempted = marksUnattempted,
                rollDigits = rollDigits,
                dateString = dateString,
                includeNameField = includeNameField,
                includeRollField = includeRollField,
                includeDateField = includeDateField,
                includeBatchField = includeBatchField,
                includePhoneField = includePhoneField,
                topicsJson = topicsJson,
                optionalRulesJson = optionalRulesDescription,
                answerKeyConfigured = false // Key will be configured later by tapping OMR bubbles!
            )
            val assessmentId = repository.createAssessment(assessment)
            navigateTo(AppScreen.ExamDetails(assessmentId))
        }
    }

    // Requirement 7: Load and edit answer key interactively on the generated OMR sheet
    fun loadAnswerKeyForEditor(assessmentId: Long, setCode: String = "A") {
        viewModelScope.launch {
            _editingSetCode.value = setCode
            val assessment = repository.getAssessment(assessmentId)
            val totalQ = assessment?.totalQuestions ?: 30
            val existing = repository.getAnswerKey(assessmentId, setCode)
            if (existing != null && existing.answersListString.isNotBlank()) {
                val list = existing.answersListString.split(",").map { it.trim().uppercase() }
                _editingAnswerKey.value = (0 until totalQ).map { idx ->
                    list.getOrNull(idx) ?: "-"
                }
            } else {
                _editingAnswerKey.value = (0 until totalQ).map { "-" }
            }
        }
    }

    fun setAnswerForQuestion(questionIndex: Int, answer: String) {
        val current = _editingAnswerKey.value.toMutableList()
        if (questionIndex in current.indices) {
            current[questionIndex] = if (current[questionIndex] == answer) "-" else answer
            _editingAnswerKey.value = current
        }
    }

    fun fillAllRemainingWithOption(option: String) {
        val current = _editingAnswerKey.value.toMutableList()
        for (i in current.indices) {
            if (current[i] == "-") {
                current[i] = option
            }
        }
        _editingAnswerKey.value = current
    }

    fun clearAllAnswers() {
        val count = _editingAnswerKey.value.size
        _editingAnswerKey.value = (0 until count).map { "-" }
    }

    fun saveEditingAnswerKey(assessmentId: Long, onSaved: () -> Unit) {
        viewModelScope.launch {
            val setCode = _editingSetCode.value
            val current = _editingAnswerKey.value
            // Fill any unfilled with 'A' as safe fallback
            val finalized = current.map { if (it == "-") "A" else it }
            repository.saveAnswerKey(assessmentId, setCode, finalized)
            onSaved()
        }
    }

    // Grading & Results
    fun confirmAndSaveGrade(
        assessmentId: Long,
        studentRoll: String,
        setCode: String,
        markedAnswers: List<String>,
        onSuccess: (ScannedResultEntity) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.gradeAndSaveOmrSheet(
                assessmentId = assessmentId,
                studentRoll = studentRoll,
                setCode = setCode,
                markedAnswers = markedAnswers
            )
            onSuccess(result)
        }
    }

    suspend fun checkExistingResult(assessmentId: Long, rollNumber: String): ScannedResultEntity? {
        return repository.getExistingResult(assessmentId, rollNumber)
    }

    fun deleteScoreResult(resultId: Long) {
        viewModelScope.launch {
            AppDatabase.getDatabase(getApplication()).scannedResultDao().deleteResult(resultId)
            navigateBack()
        }
    }

    fun deleteAssessment(assessmentId: Long) {
        viewModelScope.launch {
            repository.deleteAssessment(assessmentId)
            navigateTo(AppScreen.InstituteDashboard)
        }
    }

    fun addNewBatch(name: String, description: String, classLevel: String, academicYear: String) {
        viewModelScope.launch {
            repository.addNewBatch(name, description, classLevel, academicYear)
        }
    }

    fun updateBatch(batch: BatchEntity) {
        viewModelScope.launch {
            repository.updateBatch(batch)
        }
    }

    fun deleteBatch(id: Long) {
        viewModelScope.launch {
            repository.deleteBatch(id)
        }
    }

    fun addNewStudent(
        roll: String,
        name: String,
        batchId: Long,
        batchName: String,
        email: String,
        phone: String,
        whatsapp: String
    ) {
        viewModelScope.launch {
            repository.addNewStudent(roll, name, batchId, batchName, email, phone, whatsapp)
        }
    }

    fun updateStudent(student: StudentEntity) {
        viewModelScope.launch {
            repository.updateStudent(student)
        }
    }

    fun deleteStudent(rollNumber: String) {
        viewModelScope.launch {
            repository.deleteStudent(rollNumber)
        }
    }

    fun updateStudentContact(roll: String, phone: String, email: String, whatsapp: String) {
        viewModelScope.launch {
            repository.updateStudentContact(roll, phone, email, whatsapp)
        }
    }

    suspend fun getDetailedEvaluations(result: ScannedResultEntity): List<QuestionEvaluation> {
        return repository.getDetailedEvaluations(result)
    }

    suspend fun getAssessmentById(id: Long): AssessmentEntity? {
        return repository.getAssessment(id)
    }

    fun getTopicsForAssessment(topicsJson: String): List<TopicDefinition> {
        return repository.parseTopics(topicsJson)
    }

    fun getTopicResults(resultsJson: String): List<TopicScoreResult> {
        return repository.parseTopicResults(resultsJson)
    }

    private fun serializeTopics(topics: List<TopicDefinition>): String {
        val arr = JSONArray()
        topics.forEach { t ->
            val obj = JSONObject().apply {
                put("name", t.name)
                put("startQuestion", t.startQuestion)
                put("endQuestion", t.endQuestion)
                if (t.maxAttempts != null) put("maxAttempts", t.maxAttempts)
            }
            arr.put(obj)
        }
        return arr.toString()
    }
}
