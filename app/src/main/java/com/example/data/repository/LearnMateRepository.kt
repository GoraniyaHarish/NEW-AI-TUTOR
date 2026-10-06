package com.example.data.repository

import com.example.ai.AIRouter
import com.example.ai.LessonExplanation
import com.example.ai.TutorResponse
import com.example.ai.retrieval.LocalRetriever
import com.example.core.storage.ExtractedChunk
import com.example.data.demo.DemoDataLoader
import com.example.data.local.database.LearnMateDatabase
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.CourseEntity
import com.example.data.local.entity.DocumentChunkEntity
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.LearnerSkillEntity
import com.example.data.local.entity.LearningPlanEntity
import com.example.data.local.entity.LearningPlanItemEntity
import com.example.data.local.entity.QuestionEntity
import com.example.data.local.entity.QuizAttemptEntity
import com.example.data.local.entity.SkillEntity
import com.example.data.local.entity.SkillRelationEntity
import com.example.learning.personalization.PersonalizationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class LearnMateRepository(
    private val database: LearnMateDatabase,
    private val aiRouter: AIRouter,
    private val retriever: LocalRetriever
) {
    private val _activeCourseId = MutableStateFlow<Long?>(null)
    val activeCourseId: StateFlow<Long?> = _activeCourseId.asStateFlow()

    fun setActiveCourse(id: Long) {
        _activeCourseId.value = id
    }

    fun getAllCourses(): Flow<List<CourseEntity>> = database.courseDao().getAllCourses()

    suspend fun getCourse(id: Long): CourseEntity? = database.courseDao().getCourseById(id)

    fun getDocuments(courseId: Long): Flow<List<DocumentEntity>> =
        database.documentDao().getDocumentsForCourse(courseId)

    fun getChunks(courseId: Long): Flow<List<DocumentChunkEntity>> =
        database.documentChunkDao().getChunksForCourse(courseId)

    fun getSkills(courseId: Long): Flow<List<SkillEntity>> =
        database.skillDao().getSkillsForCourse(courseId)

    fun getRelations(courseId: Long): Flow<List<SkillRelationEntity>> =
        database.skillRelationDao().getRelationsForCourse(courseId)

    fun getLearnerSkills(courseId: Long): Flow<List<LearnerSkillEntity>> =
        database.learnerSkillDao().getLearnerSkillsForCourse(courseId)

    fun getQuestions(courseId: Long): Flow<List<QuestionEntity>> =
        database.questionDao().getQuestionsForCourse(courseId)

    fun getAttempts(courseId: Long): Flow<List<QuizAttemptEntity>> =
        database.quizAttemptDao().getAttemptsForCourse(courseId)

    fun getLatestPlan(courseId: Long): Flow<LearningPlanEntity?> =
        database.learningPlanDao().getLatestPlan(courseId)

    fun getPlanItems(planId: Long): Flow<List<LearningPlanItemEntity>> =
        database.learningPlanDao().getPlanItems(planId)

    fun getChatMessages(courseId: Long): Flow<List<ChatMessageEntity>> =
        database.chatMessageDao().getMessagesForCourse(courseId)

    suspend fun createCourse(title: String, description: String): Long = withContext(Dispatchers.IO) {
        val course = CourseEntity(title = title, description = description, isDemo = false)
        val id = database.courseDao().insertCourse(course)
        _activeCourseId.value = id
        id
    }

    suspend fun addDocument(
        courseId: Long,
        fileName: String,
        fileType: String,
        fileSize: String,
        extractedChunks: List<ExtractedChunk>
    ): Long = withContext(Dispatchers.IO) {
        val docId = database.documentDao().insertDocument(
            DocumentEntity(
                courseId = courseId,
                fileName = fileName,
                filePath = "local/$fileName",
                fileType = fileType,
                fileSize = fileSize,
                pageCount = maxOf(1, extractedChunks.maxOfOrNull { it.pageNumber } ?: 1),
                processed = false
            )
        )

        val chunkEntities = extractedChunks.map {
            DocumentChunkEntity(
                documentId = docId,
                courseId = courseId,
                sourceDocumentName = fileName,
                pageNumber = it.pageNumber,
                text = it.text,
                chunkIndex = it.chunkIndex
            )
        }
        database.documentChunkDao().insertChunks(chunkEntities)
        docId
    }

    suspend fun processMaterialAndBuildSkillMap(courseId: Long) = withContext(Dispatchers.IO) {
        val docs = database.documentDao().getDocumentsSync(courseId)
        val chunks = database.documentChunkDao().getChunksSync(courseId)

        // Mark documents as processed
        for (doc in docs) {
            database.documentDao().updateProcessed(doc.id, true)
        }

        // If no skills exist yet, extract skills from document text/chunks
        val existingSkills = database.skillDao().getSkillsSync(courseId)
        if (existingSkills.isEmpty() && chunks.isNotEmpty()) {
            val primaryDoc = docs.firstOrNull()
            val extractedSkills = buildSkillsFromMaterial(courseId, primaryDoc?.fileName ?: "Material", chunks)
            val skillIds = database.skillDao().insertSkills(extractedSkills)

            // Setup basic relationships
            val relations = mutableListOf<SkillRelationEntity>()
            for (i in 0 until skillIds.size - 1) {
                relations.add(
                    SkillRelationEntity(
                        courseId = courseId,
                        fromSkillId = skillIds[i],
                        toSkillId = skillIds[i + 1],
                        relationType = "PREREQUISITE"
                    )
                )
            }
            database.skillRelationDao().insertRelations(relations)

            // Initialize learner skills (not assessed)
            val learnerSkills = skillIds.map { id ->
                LearnerSkillEntity(
                    skillId = id,
                    courseId = courseId,
                    masteryScore = 0,
                    confidence = 0.5f,
                    attempts = 0
                )
            }
            database.learnerSkillDao().insertLearnerSkills(learnerSkills)

            // Generate initial questions
            val questions = mutableListOf<QuestionEntity>()
            extractedSkills.zip(skillIds).forEach { (skill, id) ->
                questions.addAll(aiRouter.generateQuestionsForSkill(skill.copy(id = id), 2, "MEDIUM"))
            }
            database.questionDao().insertQuestions(questions)
        }

        refreshLearningPlan(courseId)
    }

    private fun buildSkillsFromMaterial(
        courseId: Long,
        docName: String,
        chunks: List<DocumentChunkEntity>
    ): List<SkillEntity> {
        val joined = chunks.joinToString(" ") { it.text }.lowercase()
        val skills = mutableListOf<SkillEntity>()

        if (joined.contains("kinematic") || joined.contains("motion") || joined.contains("displacement")) {
            skills.add(SkillEntity(courseId = courseId, name = "Kinematics", description = "Position, displacement, speed and velocity analysis.", chapter = "Mechanics", difficulty = "EASY", sourceDocumentName = docName, sourcePage = 1, confidence = 0.95f))
        }
        if (joined.contains("velocity") || joined.contains("speed")) {
            skills.add(SkillEntity(courseId = courseId, name = "Velocity & Acceleration", description = "Time derivatives of position, uniform acceleration equations.", chapter = "Mechanics", difficulty = "EASY", sourceDocumentName = docName, sourcePage = 2, confidence = 0.93f))
        }
        if (joined.contains("newton") || joined.contains("inertia") || joined.contains("momentum")) {
            skills.add(SkillEntity(courseId = courseId, name = "Newton's Laws", description = "First, Second, and Third laws governing forces and momentum.", chapter = "Dynamics", difficulty = "HARD", sourceDocumentName = docName, sourcePage = 3, confidence = 0.96f))
        }
        if (joined.contains("force") || joined.contains("free-body") || joined.contains("equilibrium")) {
            skills.add(SkillEntity(courseId = courseId, name = "Force Diagrams", description = "Vector resolution of normal, tension, and gravitational forces.", chapter = "Dynamics", difficulty = "MEDIUM", sourceDocumentName = docName, sourcePage = 4, confidence = 0.92f))
        }
        if (joined.contains("friction") || joined.contains("sliding") || joined.contains("incline")) {
            skills.add(SkillEntity(courseId = courseId, name = "Friction Analysis", description = "Static friction limits, kinetic friction, and roughness factors.", chapter = "Dynamics", difficulty = "HARD", sourceDocumentName = docName, sourcePage = 5, confidence = 0.90f))
        }
        if (joined.contains("energy") || joined.contains("work") || joined.contains("power")) {
            skills.add(SkillEntity(courseId = courseId, name = "Work and Energy", description = "Work-energy theorem and conservation of mechanical energy.", chapter = "Energy", difficulty = "MEDIUM", sourceDocumentName = docName, sourcePage = 6, confidence = 0.91f))
        }

        if (skills.isEmpty()) {
            skills.add(SkillEntity(courseId = courseId, name = "Core Principles", description = "Fundamental definitions and theoretical models.", chapter = "Overview", difficulty = "EASY", sourceDocumentName = docName, sourcePage = 1, confidence = 0.85f))
            skills.add(SkillEntity(courseId = courseId, name = "Applied Problem Solving", description = "Quantitative equations and calculations.", chapter = "Overview", difficulty = "MEDIUM", sourceDocumentName = docName, sourcePage = 2, confidence = 0.88f))
        }

        return skills
    }

    suspend fun refreshLearningPlan(courseId: Long) = withContext(Dispatchers.IO) {
        val skills = database.skillDao().getSkillsSync(courseId)
        val relations = database.skillRelationDao().getRelationsSync(courseId)
        val learnerSkills = database.learnerSkillDao().getLearnerSkillsSync(courseId)

        val (plan, items) = PersonalizationEngine.buildDailyLearningPlan(courseId, skills, relations, learnerSkills)
        val planId = database.learningPlanDao().insertPlan(plan)
        val itemsWithPlanId = items.map { it.copy(planId = planId) }
        database.learningPlanDao().insertPlanItems(itemsWithPlanId)
    }

    suspend fun updateLearnerSkill(learnerSkill: LearnerSkillEntity) = withContext(Dispatchers.IO) {
        database.learnerSkillDao().upsertLearnerSkill(learnerSkill)
        refreshLearningPlan(learnerSkill.courseId)
    }

    suspend fun recordQuizAttempt(attempt: QuizAttemptEntity) = withContext(Dispatchers.IO) {
        database.quizAttemptDao().insertAttempt(attempt)
    }

    suspend fun markPlanItemCompleted(itemId: Long, completed: Boolean) = withContext(Dispatchers.IO) {
        database.learningPlanDao().updateItemCompleted(itemId, completed)
    }

    suspend fun askTutor(courseId: Long, query: String, skillId: Long?): TutorResponse = withContext(Dispatchers.IO) {
        // Record user message
        database.chatMessageDao().insertMessage(
            ChatMessageEntity(
                courseId = courseId,
                skillId = skillId,
                role = "user",
                content = query
            )
        )

        // Retrieve chunks
        val allChunks = database.documentChunkDao().getChunksSync(courseId)
        val retrieved = retriever.search(query, allChunks, topK = 4)
        val relevantChunks = retrieved.map { it.chunk }

        val skill = if (skillId != null) database.skillDao().getSkillById(skillId) else null

        val response = aiRouter.answerTutor(query, skill, relevantChunks, courseId)

        // Record assistant message
        database.chatMessageDao().insertMessage(
            ChatMessageEntity(
                courseId = courseId,
                skillId = skillId,
                role = "assistant",
                content = response.answer,
                sourceDocumentName = response.sourceDocName,
                sourcePage = response.sourcePage,
                isOfflineGenerated = response.isOffline
            )
        )

        response
    }

    suspend fun getLessonExplanation(skill: SkillEntity): LessonExplanation = withContext(Dispatchers.IO) {
        val allChunks = database.documentChunkDao().getChunksSync(skill.courseId)
        val retrieved = retriever.search(skill.name, allChunks, topK = 3)
        aiRouter.generateExplanation(skill, retrieved.map { it.chunk })
    }

    suspend fun resetToDemoCourse(): Long = withContext(Dispatchers.IO) {
        val courseId = DemoDataLoader.populateDemoCourse(database)
        _activeCourseId.value = courseId
        courseId
    }

    suspend fun ensureInitialData() = withContext(Dispatchers.IO) {
        DemoDataLoader.populateDemoCourseIfEmpty(database)
        val firstCourse = database.courseDao().getFirstCourse()
        if (_activeCourseId.value == null && firstCourse != null) {
            _activeCourseId.value = firstCourse.id
        }
    }
}
