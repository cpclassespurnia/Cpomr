package com.example.data.local

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Dao
interface BatchDao {
    @Query("SELECT * FROM batches ORDER BY id ASC")
    fun getAllBatches(): Flow<List<BatchEntity>>

    @Query("SELECT * FROM batches WHERE id = :id")
    suspend fun getBatchById(id: Long): BatchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(batch: BatchEntity): Long

    @Update
    suspend fun updateBatch(batch: BatchEntity)

    @Query("DELETE FROM batches WHERE id = :id")
    suspend fun deleteBatch(id: Long)
}

@Dao
interface StudentDao {
    @Query("SELECT * FROM students ORDER BY rollNumber ASC")
    fun getAllStudents(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE batchId = :batchId ORDER BY rollNumber ASC")
    fun getStudentsByBatch(batchId: Long): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE rollNumber = :rollNumber LIMIT 1")
    suspend fun getStudentByRoll(rollNumber: String): StudentEntity?

    @Query("SELECT * FROM students WHERE LOWER(TRIM(email)) = LOWER(TRIM(:email)) LIMIT 1")
    suspend fun getStudentByEmail(email: String): StudentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: StudentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(students: List<StudentEntity>)

    @Query("UPDATE students SET phone = :phone, email = :email, whatsapp = :whatsapp WHERE rollNumber = :roll")
    suspend fun updateContacts(roll: String, phone: String, email: String, whatsapp: String)

    @Update
    suspend fun updateStudent(student: StudentEntity)

    @Query("DELETE FROM students WHERE rollNumber = :rollNumber")
    suspend fun deleteStudent(rollNumber: String)
}

@Dao
interface StaffDao {
    @Query("SELECT * FROM staff ORDER BY id ASC")
    fun getAllStaff(): Flow<List<StaffEntity>>

    @Query("SELECT * FROM staff WHERE LOWER(TRIM(email)) = LOWER(TRIM(:email)) LIMIT 1")
    suspend fun getStaffByEmail(email: String): StaffEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStaff(staff: StaffEntity): Long

    @Update
    suspend fun updateStaff(staff: StaffEntity)

    @Query("DELETE FROM staff WHERE id = :id")
    suspend fun deleteStaff(id: Long)
}

@Dao
interface AssessmentDao {
    @Query("SELECT * FROM assessments ORDER BY id DESC")
    fun getAllAssessments(): Flow<List<AssessmentEntity>>

    @Query("SELECT * FROM assessments WHERE id = :id LIMIT 1")
    suspend fun getAssessmentById(id: Long): AssessmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssessment(assessment: AssessmentEntity): Long

    @Update
    suspend fun updateAssessment(assessment: AssessmentEntity)

    @Query("DELETE FROM assessments WHERE id = :id")
    suspend fun deleteAssessment(id: Long)
}

@Dao
interface AnswerKeyDao {
    @Query("SELECT * FROM answer_keys WHERE assessmentId = :assessmentId")
    fun getKeysForAssessment(assessmentId: Long): Flow<List<AnswerKeyEntity>>

    @Query("SELECT * FROM answer_keys WHERE assessmentId = :assessmentId AND setCode = :setCode LIMIT 1")
    suspend fun getKey(assessmentId: Long, setCode: String): AnswerKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnswerKey(key: AnswerKeyEntity)

    @Update
    suspend fun updateAnswerKey(key: AnswerKeyEntity)

    @Query("DELETE FROM answer_keys WHERE assessmentId = :assessmentId")
    suspend fun deleteKeysForAssessment(assessmentId: Long)
}

@Dao
interface ScannedResultDao {
    @Query("SELECT * FROM scanned_results ORDER BY id DESC")
    fun getAllResults(): Flow<List<ScannedResultEntity>>

    @Query("SELECT * FROM scanned_results WHERE assessmentId = :assessmentId ORDER BY score DESC, correctCount DESC")
    fun getResultsForAssessment(assessmentId: Long): Flow<List<ScannedResultEntity>>

    @Query("SELECT * FROM scanned_results WHERE studentRollNumber = :rollNumber ORDER BY timestamp DESC")
    fun getResultsForStudent(rollNumber: String): Flow<List<ScannedResultEntity>>

    @Query("SELECT * FROM scanned_results WHERE id = :id LIMIT 1")
    suspend fun getResultById(id: Long): ScannedResultEntity?

    @Query("SELECT * FROM scanned_results WHERE assessmentId = :assessmentId AND LOWER(studentRollNumber) = LOWER(:rollNumber) LIMIT 1")
    suspend fun getResultForStudentInAssessment(assessmentId: Long, rollNumber: String): ScannedResultEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: ScannedResultEntity): Long

    @Query("DELETE FROM scanned_results WHERE id = :id")
    suspend fun deleteResult(id: Long)

    @Query("DELETE FROM scanned_results WHERE assessmentId = :assessmentId")
    suspend fun deleteResultsForAssessment(assessmentId: Long)
}

@Database(
    entities = [
        BatchEntity::class,
        StudentEntity::class,
        StaffEntity::class,
        AssessmentEntity::class,
        AnswerKeyEntity::class,
        ScannedResultEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun batchDao(): BatchDao
    abstract fun studentDao(): StudentDao
    abstract fun staffDao(): StaffDao
    abstract fun assessmentDao(): AssessmentDao
    abstract fun answerKeyDao(): AnswerKeyDao
    abstract fun scannedResultDao(): ScannedResultDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cp_classes_purnia_omr.db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            INSTANCE?.let { database ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    seedInitialData(database)
                                }
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun seedInitialData(db: AppDatabase) {
            val batch1Id = db.batchDao().insertBatch(
                BatchEntity(
                    name = "Rankers JEE 2026 (Batch A)",
                    description = "Advanced Problem Solving & Mock Tests for JEE Main + Advanced",
                    classLevel = "Class 12",
                    academicYear = "2025-2026",
                    studentCount = 28
                )
            )
            val batch2Id = db.batchDao().insertBatch(
                BatchEntity(
                    name = "Achievers NEET 2026 (Batch N)",
                    description = "NCERT Mastery & Regular OMR Testing for NEET-UG",
                    classLevel = "Class 12",
                    academicYear = "2025-2026",
                    studentCount = 35
                )
            )
            val batch3Id = db.batchDao().insertBatch(
                BatchEntity(
                    name = "Target Droppers 2026",
                    description = "Full Syllabus Intensive Test Series",
                    classLevel = "Dropper",
                    academicYear = "2025-2026",
                    studentCount = 22
                )
            )

            // Seed registered students with whitelisted Gmail accounts
            val students = listOf(
                StudentEntity(
                    rollNumber = "CP25-01",
                    name = "Aarav Sharma",
                    batchId = batch1Id,
                    batchName = "Rankers JEE 2026 (Batch A)",
                    phone = "+91 98351 24567",
                    whatsapp = "+91 98351 24567",
                    email = "mdsalikalam@gmail.com", // Matches logged in device user email
                    dob = "12/04/2007"
                ),
                StudentEntity(
                    rollNumber = "CP25-02",
                    name = "Priya Verma",
                    batchId = batch2Id,
                    batchName = "Achievers NEET 2026 (Batch N)",
                    phone = "+91 98352 34568",
                    whatsapp = "+91 98352 34568",
                    email = "priya.verma@gmail.com",
                    dob = "23/08/2007"
                ),
                StudentEntity(
                    rollNumber = "CP25-03",
                    name = "Rohan Kumar Gupta",
                    batchId = batch1Id,
                    batchName = "Rankers JEE 2026 (Batch A)",
                    phone = "+91 98353 45679",
                    whatsapp = "+91 98353 45679",
                    email = "rohan.gupta@gmail.com",
                    dob = "15/11/2006"
                ),
                StudentEntity(
                    rollNumber = "CP25-04",
                    name = "Ananya Singh",
                    batchId = batch2Id,
                    batchName = "Achievers NEET 2026 (Batch N)",
                    phone = "+91 98354 56780",
                    whatsapp = "+91 98354 56780",
                    email = "ananya.singh@gmail.com",
                    dob = "05/02/2008"
                ),
                StudentEntity(
                    rollNumber = "CP25-05",
                    name = "Md Sameer Alam",
                    batchId = batch3Id,
                    batchName = "Target Droppers 2026",
                    phone = "+91 98355 67891",
                    whatsapp = "+91 98355 67891",
                    email = "sameer.alam@gmail.com",
                    dob = "19/09/2006"
                )
            )
            db.studentDao().insertAll(students)

            // Seed staff members with roles and permissions
            db.staffDao().insertStaff(
                StaffEntity(
                    name = "Prof. Chandan Prasad",
                    email = "director@cpclasses.in",
                    role = "Institute Admin",
                    canScanOmr = true,
                    canEditAnswerKeys = true,
                    canCreateAssessments = true,
                    canManageStudentsAndBatches = true,
                    canSendReports = true,
                    canDeleteResults = true
                )
            )
            db.staffDao().insertStaff(
                StaffEntity(
                    name = "Mohd Salik Alam (Admin)",
                    email = "mdsalikalam@gmail.com",
                    role = "Institute Admin",
                    canScanOmr = true,
                    canEditAnswerKeys = true,
                    canCreateAssessments = true,
                    canManageStudentsAndBatches = true,
                    canSendReports = true,
                    canDeleteResults = true
                )
            )
            db.staffDao().insertStaff(
                StaffEntity(
                    name = "Er. Rajesh Kumar",
                    email = "rajesh.physics@gmail.com",
                    role = "Evaluator / Teacher",
                    canScanOmr = true,
                    canEditAnswerKeys = true,
                    canCreateAssessments = true,
                    canManageStudentsAndBatches = false,
                    canSendReports = true,
                    canDeleteResults = false
                )
            )
            db.staffDao().insertStaff(
                StaffEntity(
                    name = "Amit Singh",
                    email = "amit.scanner@gmail.com",
                    role = "Scanner Staff",
                    canScanOmr = true,
                    canEditAnswerKeys = false,
                    canCreateAssessments = false,
                    canManageStudentsAndBatches = false,
                    canSendReports = false,
                    canDeleteResults = false
                )
            )

            // Seed sample assessments with topics & optional rules
            val neetTopics = """[{"name":"Physics","startQuestion":1,"endQuestion":45,"maxAttempts":null},{"name":"Chemistry","startQuestion":46,"endQuestion":90,"maxAttempts":null},{"name":"Botany","startQuestion":91,"endQuestion":135,"maxAttempts":null},{"name":"Zoology","startQuestion":136,"endQuestion":180,"maxAttempts":null}]"""
            val assess1Id = db.assessmentDao().insertAssessment(
                AssessmentEntity(
                    title = "NEET Full Syllabus Grand Test 01",
                    batchId = batch2Id,
                    batchName = "Achievers NEET 2026 (Batch N)",
                    subject = "PCB (Physics, Chemistry, Biology)",
                    totalQuestions = 180,
                    marksPerCorrect = 4.0f,
                    negativeMarksPerWrong = 1.0f,
                    marksUnattempted = 0.0f,
                    rollDigits = 4,
                    dateString = "2026-10-22",
                    topicsJson = neetTopics,
                    answerKeyConfigured = true
                )
            )

            val jeeTopics = """[{"name":"Physics Sec A","startQuestion":1,"endQuestion":20,"maxAttempts":null},{"name":"Physics Sec B (Opt 5/10)","startQuestion":21,"endQuestion":30,"maxAttempts":5},{"name":"Chemistry Sec A","startQuestion":31,"endQuestion":50,"maxAttempts":null},{"name":"Chemistry Sec B (Opt 5/10)","startQuestion":51,"endQuestion":60,"maxAttempts":5},{"name":"Math Sec A","startQuestion":61,"endQuestion":80,"maxAttempts":null},{"name":"Math Sec B (Opt 5/10)","startQuestion":81,"endQuestion":90,"maxAttempts":5}]"""
            val assess2Id = db.assessmentDao().insertAssessment(
                AssessmentEntity(
                    title = "JEE Main Mock Test 02",
                    batchId = batch1Id,
                    batchName = "Rankers JEE 2026 (Batch A)",
                    subject = "PCM (Physics, Chemistry, Mathematics)",
                    totalQuestions = 90,
                    marksPerCorrect = 4.0f,
                    negativeMarksPerWrong = 1.0f,
                    marksUnattempted = 0.0f,
                    rollDigits = 4,
                    dateString = "2026-10-22",
                    topicsJson = jeeTopics,
                    optionalRulesJson = "Attempt any 5 out of 10 in Section B",
                    answerKeyConfigured = true
                )
            )

            // Keys for Test 1
            val options = listOf("A", "B", "C", "D")
            val setAKeys = (0 until 180).map { options[it % 4] }
            db.answerKeyDao().insertAnswerKey(
                AnswerKeyEntity(
                    assessmentId = assess1Id,
                    setCode = "A",
                    answersListString = setAKeys.joinToString(",")
                )
            )

            // Keys for Test 2
            val jeeKeys = (0 until 90).map { options[(it * 2 + 1) % 4] }
            db.answerKeyDao().insertAnswerKey(
                AnswerKeyEntity(
                    assessmentId = assess2Id,
                    setCode = "A",
                    answersListString = jeeKeys.joinToString(",")
                )
            )

            // Seed sample scanned results
            val studentMarked = setAKeys.mapIndexed { idx, ans ->
                when {
                    idx % 15 == 0 -> "-"
                    idx % 9 == 0 -> options[(idx + 1) % 4]
                    else -> ans
                }
            }
            db.scannedResultDao().insertResult(
                ScannedResultEntity(
                    assessmentId = assess1Id,
                    assessmentTitle = "NEET Full Syllabus Grand Test 01",
                    studentRollNumber = "CP25-01",
                    studentName = "Aarav Sharma",
                    batchName = "Achievers NEET 2026 (Batch N)",
                    setCode = "A",
                    markedAnswersString = studentMarked.joinToString(","),
                    totalQuestions = 180,
                    correctCount = 152,
                    wrongCount = 16,
                    unattemptedCount = 12,
                    multiMarkedCount = 0,
                    score = 592.0f,
                    maxScore = 720.0f,
                    percentage = 82.2f,
                    accuracy = 90.5f,
                    rank = 2,
                    percentile = 96.8f,
                    topperScore = 640.0f,
                    topperName = "Priya Verma",
                    timestamp = System.currentTimeMillis() - 86400000
                )
            )

            val priyaMarked = setAKeys.mapIndexed { idx, ans ->
                if (idx % 18 == 0) options[(idx + 2) % 4] else ans
            }
            db.scannedResultDao().insertResult(
                ScannedResultEntity(
                    assessmentId = assess1Id,
                    assessmentTitle = "NEET Full Syllabus Grand Test 01",
                    studentRollNumber = "CP25-02",
                    studentName = "Priya Verma",
                    batchName = "Achievers NEET 2026 (Batch N)",
                    setCode = "A",
                    markedAnswersString = priyaMarked.joinToString(","),
                    totalQuestions = 180,
                    correctCount = 165,
                    wrongCount = 10,
                    unattemptedCount = 5,
                    multiMarkedCount = 0,
                    score = 650.0f,
                    maxScore = 720.0f,
                    percentage = 90.3f,
                    accuracy = 94.3f,
                    rank = 1,
                    percentile = 100.0f,
                    topperScore = 650.0f,
                    topperName = "Priya Verma",
                    timestamp = System.currentTimeMillis() - 86400000
                )
            )
        }
    }
}
