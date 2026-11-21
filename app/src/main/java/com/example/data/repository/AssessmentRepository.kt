package com.example.data.repository

import android.graphics.Bitmap
import com.example.data.local.AppDatabase
import com.example.data.model.*
import com.example.omr.OmrVisionEngine
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class AssessmentRepository(private val db: AppDatabase) {

    val allAssessments = db.assessmentDao().getAllAssessments()
    val allBatches = db.batchDao().getAllBatches()
    val allStudents = db.studentDao().getAllStudents()
    val allStaff = db.staffDao().getAllStaff()
    val allResults = db.scannedResultDao().getAllResults()

    suspend fun getAssessment(id: Long): AssessmentEntity? {
        return db.assessmentDao().getAssessmentById(id)
    }

    suspend fun createAssessment(assessment: AssessmentEntity): Long {
        return db.assessmentDao().insertAssessment(assessment)
    }

    suspend fun updateAssessment(assessment: AssessmentEntity) {
        db.assessmentDao().updateAssessment(assessment)
    }

    suspend fun deleteAssessment(id: Long) {
        db.assessmentDao().deleteAssessment(id)
        db.answerKeyDao().deleteKeysForAssessment(id)
        db.scannedResultDao().deleteResultsForAssessment(id)
    }

    suspend fun getAnswerKey(assessmentId: Long, setCode: String = "A"): AnswerKeyEntity? {
        return db.answerKeyDao().getKey(assessmentId, setCode)
    }

    suspend fun saveAnswerKey(assessmentId: Long, setCode: String, answersList: List<String>) {
        val existing = db.answerKeyDao().getKey(assessmentId, setCode)
        val answersStr = answersList.joinToString(",")
        if (existing != null) {
            db.answerKeyDao().updateAnswerKey(existing.copy(answersListString = answersStr))
        } else {
            db.answerKeyDao().insertAnswerKey(
                AnswerKeyEntity(
                    assessmentId = assessmentId,
                    setCode = setCode,
                    answersListString = answersStr
                )
            )
        }
        // Mark answerKeyConfigured on assessment
        val assessment = db.assessmentDao().getAssessmentById(assessmentId)
        if (assessment != null && !assessment.answerKeyConfigured) {
            db.assessmentDao().updateAssessment(assessment.copy(answerKeyConfigured = true))
        }
    }

    suspend fun addNewBatch(name: String, description: String, classLevel: String, academicYear: String): Long {
        return db.batchDao().insertBatch(
            BatchEntity(
                name = name,
                description = description,
                classLevel = classLevel,
                academicYear = academicYear
            )
        )
    }

    suspend fun addNewStudent(roll: String, name: String, batchId: Long, batchName: String, email: String, phone: String, whatsapp: String) {
        db.studentDao().insertStudent(
            StudentEntity(
                rollNumber = roll,
                name = name,
                batchId = batchId,
                batchName = batchName,
                email = email,
                phone = phone,
                whatsapp = whatsapp
            )
        )
    }

    suspend fun updateBatch(batch: BatchEntity) {
        db.batchDao().updateBatch(batch)
    }

    suspend fun deleteBatch(id: Long) {
        db.batchDao().deleteBatch(id)
    }

    suspend fun updateStudent(student: StudentEntity) {
        db.studentDao().updateStudent(student)
    }

    suspend fun deleteStudent(rollNumber: String) {
        db.studentDao().deleteStudent(rollNumber)
    }

    suspend fun updateStudentContact(roll: String, phone: String, email: String, whatsapp: String) {
        db.studentDao().updateContacts(roll, phone, email, whatsapp)
    }

    suspend fun getStudentByRoll(rollNumber: String): StudentEntity? {
        return db.studentDao().getStudentByRoll(rollNumber)
    }

    suspend fun getStudentByEmail(email: String): StudentEntity? {
        return db.studentDao().getStudentByEmail(email)
    }

    suspend fun getStaffByEmail(email: String): StaffEntity? {
        return db.staffDao().getStaffByEmail(email)
    }

    suspend fun addNewStaff(staff: StaffEntity): Long {
        return db.staffDao().insertStaff(staff)
    }

    suspend fun updateStaff(staff: StaffEntity) {
        db.staffDao().updateStaff(staff)
    }

    suspend fun deleteStaff(id: Long) {
        db.staffDao().deleteStaff(id)
    }

    suspend fun gradeAndSaveOmrSheet(
        assessmentId: Long,
        studentRoll: String,
        setCode: String,
        markedAnswers: List<String>
    ): ScannedResultEntity {
        val assessment = db.assessmentDao().getAssessmentById(assessmentId)
            ?: throw IllegalArgumentException("Assessment not found")

        val student = db.studentDao().getStudentByRoll(studentRoll)
        val studentName = student?.name ?: "Student ($studentRoll)"
        val batchName = student?.batchName ?: assessment.batchName

        val answerKeyEntity = db.answerKeyDao().getKey(assessmentId, setCode)
            ?: db.answerKeyDao().getKey(assessmentId, "A")

        val answerKeyList = answerKeyEntity?.answersListString?.split(",")?.map { it.trim() }
            ?: (0 until assessment.totalQuestions).map { listOf("A", "B", "C", "D")[it % 4] }

        // Parse topics
        val topics = parseTopics(assessment.topicsJson)

        val evaluations = OmrVisionEngine.evaluate(
            markedAnswers = markedAnswers,
            answerKey = answerKeyList,
            marksPerCorrect = assessment.marksPerCorrect,
            negativeMarksPerWrong = assessment.negativeMarksPerWrong,
            marksUnattempted = assessment.marksUnattempted,
            topics = topics
        )

        var correctCount = 0
        var wrongCount = 0
        var unattemptedCount = 0
        var multiMarkedCount = 0
        var totalScore = 0.0f

        evaluations.forEach { e ->
            when (e.status) {
                QuestionStatus.CORRECT -> {
                    correctCount++
                    totalScore += e.marksAwarded
                }
                QuestionStatus.WRONG -> {
                    wrongCount++
                    totalScore += e.marksAwarded
                }
                QuestionStatus.UNATTEMPTED -> {
                    unattemptedCount++
                    totalScore += e.marksAwarded
                }
                QuestionStatus.INVALID -> {
                    multiMarkedCount++
                    totalScore += e.marksAwarded
                }
                QuestionStatus.EXCLUDED_OPTIONAL -> {
                    // 0 marks
                }
            }
        }

        val maxScore = assessment.totalQuestions * assessment.marksPerCorrect
        val percentage = if (maxScore > 0) ((totalScore / maxScore) * 100f).coerceAtLeast(0f) else 0f
        val attemptedTotal = correctCount + wrongCount + multiMarkedCount
        val accuracy = if (attemptedTotal > 0) ((correctCount.toFloat() / attemptedTotal) * 100f) else 0f

        // Compute topic-wise breakdown JSON
        val topicResults = computeTopicScoreBreakdown(evaluations, topics, assessment.marksPerCorrect)
        val topicResultsJson = serializeTopicResults(topicResults)

        // Existing results for calculating rank and topper
        val existingResults = db.scannedResultDao().getResultsForAssessment(assessmentId).first()
        val allScores = (existingResults.map { it.score } + totalScore).sortedDescending()
        val topperScore = allScores.firstOrNull() ?: totalScore
        val topperName = if (totalScore >= topperScore) studentName else (existingResults.maxByOrNull { it.score }?.studentName ?: studentName)

        val rank = allScores.indexOf(totalScore) + 1
        val percentile = if (allScores.size > 1) {
            (((allScores.size - rank).toFloat() / (allScores.size - 1)) * 100f)
        } else 100.0f

        // Requirement 6: Generate and store color-coded annotated OMR image (Green=correct, Red=incorrect, Yellow=non-attempt)
        var scannedImageUri: String? = null
        try {
            val annotatedBitmap = OmrVisionEngine.generateAnnotatedGradedOmrBitmap(
                testTitle = assessment.title,
                studentRoll = studentRoll,
                studentName = studentName,
                batchName = batchName,
                dateString = assessment.dateString,
                totalQuestions = assessment.totalQuestions,
                rollDigits = assessment.rollDigits,
                evaluations = evaluations,
                examId = assessmentId,
                score = totalScore,
                maxScore = maxScore,
                rank = rank
            )
            val dbPath = db.openHelper.writableDatabase.path
            val cacheDir = File(dbPath).parentFile ?: File("/tmp")
            val omrFolder = File(cacheDir, "annotated_omr").apply { mkdirs() }
            val cleanRoll = studentRoll.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val imageFile = File(omrFolder, "omr_${assessmentId}_${cleanRoll}.png")
            val fos = FileOutputStream(imageFile)
            annotatedBitmap.compress(Bitmap.CompressFormat.PNG, 92, fos)
            fos.close()
            scannedImageUri = imageFile.absolutePath
        } catch (_: Exception) { }

        val existingResult = db.scannedResultDao().getResultForStudentInAssessment(assessmentId, studentRoll.trim())

        val resultEntity = ScannedResultEntity(
            id = existingResult?.id ?: 0L,
            assessmentId = assessmentId,
            assessmentTitle = assessment.title,
            studentRollNumber = studentRoll,
            studentName = studentName,
            batchName = batchName,
            setCode = setCode,
            markedAnswersString = markedAnswers.joinToString(","),
            totalQuestions = assessment.totalQuestions,
            correctCount = correctCount,
            wrongCount = wrongCount,
            unattemptedCount = unattemptedCount,
            multiMarkedCount = multiMarkedCount,
            score = totalScore,
            maxScore = maxScore,
            percentage = (percentage * 10).roundToInt() / 10f,
            accuracy = (accuracy * 10).roundToInt() / 10f,
            rank = rank,
            percentile = (percentile * 10).roundToInt() / 10f,
            topperScore = topperScore,
            topperName = topperName,
            topicResultsJson = topicResultsJson,
            scannedImageUri = scannedImageUri,
            timestamp = System.currentTimeMillis()
        )

        val insertedId = db.scannedResultDao().insertResult(resultEntity)
        return resultEntity.copy(id = insertedId)
    }

    suspend fun getExistingResult(assessmentId: Long, rollNumber: String): ScannedResultEntity? {
        return db.scannedResultDao().getResultForStudentInAssessment(assessmentId, rollNumber.trim())
    }

    suspend fun getDetailedEvaluations(result: ScannedResultEntity): List<QuestionEvaluation> {
        val assessment = db.assessmentDao().getAssessmentById(result.assessmentId)
        val answerKeyEntity = db.answerKeyDao().getKey(result.assessmentId, result.setCode)
            ?: db.answerKeyDao().getKey(result.assessmentId, "A")

        val answerKeyList = answerKeyEntity?.answersListString?.split(",")?.map { it.trim() }
            ?: (0 until result.totalQuestions).map { listOf("A", "B", "C", "D")[it % 4] }

        val markedList = result.markedAnswersString.split(",").map { it.trim() }
        val topics = parseTopics(assessment?.topicsJson ?: "")

        return OmrVisionEngine.evaluate(
            markedAnswers = markedList,
            answerKey = answerKeyList,
            marksPerCorrect = assessment?.marksPerCorrect ?: 4.0f,
            negativeMarksPerWrong = assessment?.negativeMarksPerWrong ?: 1.0f,
            marksUnattempted = assessment?.marksUnattempted ?: 0.0f,
            topics = topics
        )
    }

    fun parseTopics(topicsJson: String): List<TopicDefinition> {
        if (topicsJson.isBlank()) return emptyList()
        val list = mutableListOf<TopicDefinition>()
        try {
            val arr = JSONArray(topicsJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    TopicDefinition(
                        name = obj.getString("name"),
                        startQuestion = obj.getInt("startQuestion"),
                        endQuestion = obj.getInt("endQuestion"),
                        maxAttempts = if (obj.has("maxAttempts") && !obj.isNull("maxAttempts")) obj.getInt("maxAttempts") else null
                    )
                )
            }
        } catch (_: Exception) { }
        return list
    }

    fun computeTopicScoreBreakdown(
        evaluations: List<QuestionEvaluation>,
        topics: List<TopicDefinition>,
        marksPerCorrect: Float
    ): List<TopicScoreResult> {
        if (topics.isEmpty()) {
            val total = evaluations.size
            val correct = evaluations.count { it.status == QuestionStatus.CORRECT }
            val wrong = evaluations.count { it.status == QuestionStatus.WRONG }
            val unattempted = evaluations.count { it.status == QuestionStatus.UNATTEMPTED }
            val score = evaluations.sumOf { it.marksAwarded.toDouble() }.toFloat()
            val attempted = correct + wrong
            val acc = if (attempted > 0) (correct.toFloat() / attempted) * 100f else 0f
            return listOf(
                TopicScoreResult(
                    topicName = "Overall Test",
                    totalQuestions = total,
                    attempted = attempted,
                    correct = correct,
                    wrong = wrong,
                    unattempted = unattempted,
                    score = score,
                    maxScore = total * marksPerCorrect,
                    accuracyPercent = (acc * 10).roundToInt() / 10f
                )
            )
        }

        return topics.map { topic ->
            val topicEvals = evaluations.filter { it.questionNumber in topic.startQuestion..topic.endQuestion }
            val total = topicEvals.size
            val correct = topicEvals.count { it.status == QuestionStatus.CORRECT }
            val wrong = topicEvals.count { it.status == QuestionStatus.WRONG }
            val unattempted = topicEvals.count { it.status == QuestionStatus.UNATTEMPTED }
            val score = topicEvals.sumOf { it.marksAwarded.toDouble() }.toFloat()
            val attempted = correct + wrong
            val acc = if (attempted > 0) (correct.toFloat() / attempted) * 100f else 0f
            TopicScoreResult(
                topicName = topic.name,
                totalQuestions = total,
                attempted = attempted,
                correct = correct,
                wrong = wrong,
                unattempted = unattempted,
                score = score,
                maxScore = total * marksPerCorrect,
                accuracyPercent = (acc * 10).roundToInt() / 10f,
                optionalLimit = topic.maxAttempts
            )
        }
    }

    private fun serializeTopicResults(results: List<TopicScoreResult>): String {
        val arr = JSONArray()
        results.forEach { r ->
            val obj = JSONObject().apply {
                put("topicName", r.topicName)
                put("totalQuestions", r.totalQuestions)
                put("attempted", r.attempted)
                put("correct", r.correct)
                put("wrong", r.wrong)
                put("unattempted", r.unattempted)
                put("score", r.score.toDouble())
                put("maxScore", r.maxScore.toDouble())
                put("accuracyPercent", r.accuracyPercent.toDouble())
                if (r.optionalLimit != null) put("optionalLimit", r.optionalLimit)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    fun parseTopicResults(json: String): List<TopicScoreResult> {
        if (json.isBlank()) return emptyList()
        val list = mutableListOf<TopicScoreResult>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    TopicScoreResult(
                        topicName = obj.getString("topicName"),
                        totalQuestions = obj.getInt("totalQuestions"),
                        attempted = obj.getInt("attempted"),
                        correct = obj.getInt("correct"),
                        wrong = obj.getInt("wrong"),
                        unattempted = obj.getInt("unattempted"),
                        score = obj.getDouble("score").toFloat(),
                        maxScore = obj.getDouble("maxScore").toFloat(),
                        accuracyPercent = obj.getDouble("accuracyPercent").toFloat(),
                        optionalLimit = if (obj.has("optionalLimit") && !obj.isNull("optionalLimit")) obj.getInt("optionalLimit") else null
                    )
                )
            }
        } catch (_: Exception) { }
        return list
    }
}
