package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "batches")
data class BatchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String,
    val classLevel: String = "Class 12",
    val academicYear: String = "2025-2026",
    val studentCount: Int = 0
)

@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey
    val rollNumber: String,
    val name: String,
    val batchId: Long,
    val batchName: String,
    val phone: String = "+91 98351 24567",
    val whatsapp: String = "+91 98351 24567",
    val email: String = "student@cpclasses.in",
    val dob: String = "01/01/2007"
)

@Entity(tableName = "staff")
data class StaffEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val email: String,
    val role: String = "Evaluator / Teacher",
    val canScanOmr: Boolean = true,
    val canEditAnswerKeys: Boolean = true,
    val canCreateAssessments: Boolean = true,
    val canManageStudentsAndBatches: Boolean = true,
    val canSendReports: Boolean = true,
    val canDeleteResults: Boolean = false
)

@Entity(tableName = "assessments")
data class AssessmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val batchId: Long,
    val batchName: String,
    val subject: String,
    val totalQuestions: Int = 30,
    val marksPerCorrect: Float = 4.0f,
    val negativeMarksPerWrong: Float = 1.0f,
    val marksUnattempted: Float = 0.0f,
    val rollDigits: Int = 4,
    val includeNameField: Boolean = true,
    val includeRollField: Boolean = true,
    val includeDateField: Boolean = true,
    val includeBatchField: Boolean = true,
    val includePhoneField: Boolean = true,
    val dateString: String = "2026-10-22",
    val topicsJson: String = "",
    val optionalRulesJson: String = "",
    val answerKeyConfigured: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "answer_keys")
data class AnswerKeyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val assessmentId: Long,
    val setCode: String = "A",
    val answersListString: String
)

@Entity(tableName = "scanned_results")
data class ScannedResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val assessmentId: Long,
    val assessmentTitle: String,
    val studentRollNumber: String,
    val studentName: String,
    val batchName: String,
    val setCode: String = "A",
    val markedAnswersString: String,
    val totalQuestions: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val unattemptedCount: Int,
    val multiMarkedCount: Int = 0,
    val score: Float,
    val maxScore: Float,
    val percentage: Float,
    val accuracy: Float,
    val rank: Int = 1,
    val percentile: Float = 100.0f,
    val topperScore: Float = 0.0f,
    val topperName: String = "",
    val topicResultsJson: String = "",
    val scannedImageUri: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    val scannedAtFormatted: String
        get() = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
}

enum class QuestionStatus {
    CORRECT,
    WRONG,
    UNATTEMPTED,
    INVALID,
    EXCLUDED_OPTIONAL
}

data class QuestionEvaluation(
    val questionNumber: Int,
    val correctAnswer: String,
    val studentAnswer: String,
    val status: QuestionStatus,
    val marksAwarded: Float,
    val topicName: String = ""
)

data class QuestionItemAnalysis(
    val questionNumber: Int,
    val topicName: String,
    val correctCount: Int,
    val wrongCount: Int,
    val unattemptedCount: Int,
    val accuracyPercent: Float,
    val mostCommonWrongChoice: String
)

data class TopicDefinition(
    val name: String,
    val startQuestion: Int,
    val endQuestion: Int,
    val maxAttempts: Int? = null
)

data class TopicScoreResult(
    val topicName: String,
    val totalQuestions: Int,
    val attempted: Int,
    val correct: Int,
    val wrong: Int,
    val unattempted: Int,
    val score: Float,
    val maxScore: Float,
    val accuracyPercent: Float,
    val optionalLimit: Int? = null
)

data class SubjectScore(
    val subjectName: String,
    val questionsCount: Int,
    val correct: Int,
    val wrong: Int,
    val unattempted: Int,
    val score: Float,
    val maxScore: Float
)
