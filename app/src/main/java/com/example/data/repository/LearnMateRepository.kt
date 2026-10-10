package com.example.data.repository

import com.example.ai.AIRouter
import com.example.ai.LessonExplanation
import com.example.ai.TutorResponse
import com.example.ai.local.LocalAIService
import com.example.ai.nlp.QueryUnderstanding
import com.example.ai.retrieval.LocalRetriever
import com.example.core.storage.ExtractedChunk
import androidx.room.withTransaction
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
import kotlinx.coroutines.withContext

class LearnMateRepository(
    private val database: LearnMateDatabase,
    private val aiRouter: AIRouter,
    private val retriever: LocalRetriever
) {
    private val _activeCourseId = MutableStateFlow<Long?>(null)
    private val offlineQuizGenerator = LocalAIService()
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
        extractedChunks: List<ExtractedChunk>,
        pageCount: Int = 1
    ): Long = withContext(Dispatchers.IO) {
        // Sanitize file name against path traversal
        val sanitizedFileName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        
        // Handle reprocessing/duplicate replacement cleanly
        val existingDoc = database.documentDao().findDocumentByName(courseId, fileName)
        val docId = if (existingDoc != null) {
            database.documentChunkDao().deleteChunksForDocument(existingDoc.id)
            database.documentDao().insertDocument(
                existingDoc.copy(
                    filePath = "local/$sanitizedFileName",
                    fileType = fileType,
                    fileSize = fileSize,
                    pageCount = maxOf(pageCount, extractedChunks.maxOfOrNull { it.pageNumber } ?: 1),
                    processed = false,
                    createdAt = System.currentTimeMillis()
                )
            )
            existingDoc.id
        } else {
            database.documentDao().insertDocument(
                DocumentEntity(
                    courseId = courseId,
                    fileName = fileName,
                    filePath = "local/$sanitizedFileName",
                    fileType = fileType,
                    fileSize = fileSize,
                    pageCount = maxOf(pageCount, extractedChunks.maxOfOrNull { it.pageNumber } ?: 1),
                    processed = false
                )
            )
        }

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
        if (chunkEntities.isNotEmpty()) {
            database.documentChunkDao().insertChunks(chunkEntities)
        }
        docId
    }

    suspend fun processMaterialAndBuildSkillMap(
        courseId: Long,
        onProgress: (suspend (Int, Float) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        onProgress?.invoke(0, 1f / 7f)
        val docs = database.documentDao().getDocumentsSync(courseId)
        val chunks = database.documentChunkDao().getChunksSync(courseId)

        // If there are no valid chunks across documents, mark as unprocessed and return false
        if (chunks.isEmpty() || docs.isEmpty()) {
            for (doc in docs) {
                database.documentDao().updateProcessed(doc.id, false)
            }
            return@withContext false
        }

        onProgress?.invoke(1, 2f / 7f)
        // Mark documents as processed only when chunks genuinely exist
        for (doc in docs) {
            val docChunks = chunks.filter { it.documentId == doc.id }
            val isDocValid = docChunks.isNotEmpty()
            database.documentDao().updateProcessed(doc.id, isDocValid)
        }

        onProgress?.invoke(2, 3f / 7f)
        // Extract skills from real document chunks
        val existingSkills = database.skillDao().getSkillsSync(courseId)
        
        onProgress?.invoke(3, 4f / 7f)
        if (existingSkills.isEmpty() && chunks.isNotEmpty()) {
            val primaryDoc = docs.firstOrNull()
            val extractedSkills = buildSkillsFromMaterial(courseId, primaryDoc?.fileName ?: "Material", chunks)
            
            onProgress?.invoke(4, 5f / 7f)
            if (extractedSkills.isNotEmpty()) {
                val skillIds = database.skillDao().insertSkills(extractedSkills)

                onProgress?.invoke(5, 6f / 7f)
                // Document order alone does not establish a prerequisite. Keep the graph
                // unlinked unless an explicit relationship is extracted in a later pass.

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

                // Generate initial questions using retrieved chunks
                val questions = mutableListOf<QuestionEntity>()
                extractedSkills.zip(skillIds).forEach { (skill, id) ->
                    val skillChunks = retriever.search(skill.name, chunks, courseId = courseId, topK = 4)
                        .map { it.chunk }
                    questions.addAll(
                        offlineQuizGenerator.generateQuestionsForSkill(skill.copy(id = id), 2, "MEDIUM", skillChunks)
                    )
                }
                database.questionDao().insertQuestions(questions)
            }
        } else {
            onProgress?.invoke(4, 5f / 7f)
            onProgress?.invoke(5, 6f / 7f)
        }

        onProgress?.invoke(6, 1.0f)
        refreshLearningPlan(courseId)
        true
    }

    /**
     * Dynamically extracts skills from arbitrary educational documents (Java, Physics, History, Biology, etc.)
     * without hardcoded subject keywords.
     */
    private fun buildSkillsFromMaterial(
        courseId: Long,
        docName: String,
        chunks: List<DocumentChunkEntity>
    ): List<SkillEntity> {
        val discoveredSkills = mutableListOf<SkillEntity>()
        data class SourceLine(val text: String, val nextText: String, val chunk: DocumentChunkEntity, val firstInChunk: Boolean)
        val lines = chunks.flatMap { chunk ->
            val textLines = chunk.text.lines().map(String::trim).filter(String::isNotBlank)
            textLines.mapIndexed { index, line ->
                SourceLine(line, textLines.getOrNull(index + 1).orEmpty(), chunk, index == 0)
            }
        }
        val explicitHeading = Regex("(?i)^\\s*(?:chapter|unit|module|section|topic)\\s+\\d+(?:\\.\\d+)*\\s*[:.)-]\\s*(.{3,56})\\s*$")
        val genericHeadings = setOf("equation", "overview", "introduction", "conclusion", "references", "contents", "check your understanding")

        for (item in lines) {
            val explicitName = explicitHeading.matchEntire(item.text)?.groupValues?.get(1)?.trim()
            val wordCount = item.text.split(Regex("\\s+")).size
            val looksLikeHeading = !item.firstInChunk &&
                item.text.length in 4..56 && wordCount <= 7 &&
                !item.text.contains('|') && !item.text.endsWith('.') && !item.text.endsWith(';') &&
                item.text.lowercase() !in genericHeadings &&
                item.nextText.length >= 35
            val candidateName = explicitName ?: item.text.takeIf { looksLikeHeading }
            if (!candidateName.isNullOrBlank() && discoveredSkills.none { it.name.equals(candidateName, ignoreCase = true) }) {
                discoveredSkills.add(
                    SkillEntity(
                        courseId = courseId,
                        name = candidateName.trim().trimEnd(':', '.', '-'),
                        description = item.nextText.ifBlank { "Heading found in ${item.chunk.sourceDocumentName}." },
                        chapter = "Course Topics",
                        difficulty = "MEDIUM",
                        sourceDocumentId = item.chunk.documentId,
                        sourceDocumentName = item.chunk.sourceDocumentName,
                        sourcePage = item.chunk.pageNumber,
                        confidence = if (explicitName != null) 0.92f else 0.78f
                    )
                )
            }
            if (discoveredSkills.size >= 7) break
        }

        // 2. If no explicit section markers, extract top paragraphs/chunks as core topics
        if (discoveredSkills.isEmpty()) {
            val uniqueParagraphs = chunks.take(6)
            uniqueParagraphs.forEachIndexed { index, chunk ->
                val firstSentence = chunk.text.split(Regex("(?<=[.!?])\\s+")).firstOrNull()?.trim() ?: ""
                val cleanTitle = when {
                    firstSentence.isBlank() -> ""
                    firstSentence.length <= 48 -> firstSentence
                    else -> firstSentence.take(47).trimEnd() + "…"
                }

                if (firstSentence.isNotBlank() && cleanTitle.isNotBlank()) {
                    discoveredSkills.add(
                        SkillEntity(
                            courseId = courseId,
                            name = cleanTitle,
                            description = firstSentence,
                            chapter = "Core Concepts",
                            difficulty = if (index < 2) "EASY" else if (index < 4) "MEDIUM" else "HARD",
                            sourceDocumentId = chunk.documentId,
                            sourceDocumentName = chunk.sourceDocumentName,
                            sourcePage = chunk.pageNumber,
                            confidence = 0.88f
                        )
                    )
                }
            }
        }

        // Return only genuinely discovered skills from document text (no fabricated generic placeholders)
        return discoveredSkills
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
        val skill = if (skillId != null) database.skillDao().getSkillById(skillId) else null

        val isSummary = QueryUnderstanding.analyze(query, skill?.name).intent in setOf(
            com.example.ai.nlp.TutorIntent.SUMMARY,
            com.example.ai.nlp.TutorIntent.REVISE
        )
        val courseSkills = if (isSummary && skill == null) database.skillDao().getSkillsSync(courseId) else emptyList()
        val mentionsKnownTopic = courseSkills.any { topic ->
            val topicTerms = QueryUnderstanding.analyze(topic.name).focusTerms
            topicTerms.any { term -> query.contains(term, ignoreCase = true) }
        }
        val isWholeCourseSummary = isSummary && skill == null && (
            Regex("(?i)\\b(document|documents|file|files|notes|materials|course|whole|entire|all|uploaded)\\b").containsMatchIn(query) ||
                !mentionsKnownTopic
            )
        val relevantChunks = if (isWholeCourseSummary) {
            allChunks.sortedWith(compareBy<DocumentChunkEntity> { it.documentId }.thenBy { it.pageNumber }.thenBy { it.chunkIndex })
        } else {
            retriever.search(query, allChunks, courseId = courseId, topK = 8).map { it.chunk }
        }

        // Fetch recent conversation history
        val recentHistory = database.chatMessageDao().getMessagesSync(courseId).takeLast(6)

        val response = aiRouter.answerTutor(query, skill, relevantChunks, courseId, recentHistory)

        // Record assistant message with honest citations
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

    suspend fun getCheatSheetChunks(skill: SkillEntity): List<DocumentChunkEntity> = withContext(Dispatchers.IO) {
        val courseChunks = database.documentChunkDao().getChunksSync(skill.courseId)
            .filter { it.courseId == skill.courseId && (skill.sourceDocumentId <= 0L || it.documentId == skill.sourceDocumentId) }
        if (courseChunks.isEmpty()) return@withContext emptyList()

        val byTopic = retriever.search(
            query = skill.name,
            chunks = courseChunks,
            courseId = skill.courseId,
            documentId = skill.sourceDocumentId.takeIf { it > 0L },
            topK = 8,
            minScore = 0f
        ).map { it.chunk }
        val sourcePage = courseChunks.filter { it.pageNumber == skill.sourcePage }
        (byTopic + sourcePage).distinctBy { it.id }
            .sortedWith(compareBy<DocumentChunkEntity> { it.pageNumber }.thenBy { it.chunkIndex })
    }

    suspend fun getLessonExplanation(skill: SkillEntity): LessonExplanation = withContext(Dispatchers.IO) {
        val allChunks = database.documentChunkDao().getChunksSync(skill.courseId)
        val retrieved = retriever.search(skill.name, allChunks, courseId = skill.courseId, topK = 3)
        aiRouter.generateExplanation(skill, retrieved.map { it.chunk })
    }

    suspend fun ensureInitialData() = withContext(Dispatchers.IO) {
        removeLegacyDemoCourses()
        val courses = database.courseDao().getAllCourses().first()
        for (course in courses) {
            val oldSkills = database.skillDao().getSkillsSync(course.id)
            val learnerProgress = database.learnerSkillDao().getLearnerSkillsSync(course.id)
            val attempts = database.quizAttemptDao().getAttemptsForCourseSync(course.id)
            val canSafelyRefreshMap = !course.isDemo && oldSkills.isNotEmpty() &&
                attempts.isEmpty() && learnerProgress.none { it.attempts > 0 } &&
                oldSkills.all { it.chapter in setOf("Course Module", "Core Concepts") }

            if (canSafelyRefreshMap) {
                val chunks = database.documentChunkDao().getChunksSync(course.id)
                val docs = database.documentDao().getDocumentsSync(course.id)
                val improvedSkills = buildSkillsFromMaterial(course.id, docs.firstOrNull()?.fileName ?: "Material", chunks)
                val oldNames = oldSkills.map { it.name.trim().lowercase() }.toSet()
                val newNames = improvedSkills.map { it.name.trim().lowercase() }.toSet()
                if (improvedSkills.size > oldSkills.size && !newNames.containsAll(oldNames)) {
                    // Refresh only untouched, generated maps. Any course with real practice history is preserved.
                    database.learningPlanDao().deletePlanItemsForCourse(course.id)
                    database.learningPlanDao().deletePlansForCourse(course.id)
                    database.questionDao().deleteQuestionsForCourse(course.id)
                    database.skillRelationDao().deleteRelationsForCourse(course.id)
                    database.learnerSkillDao().deleteLearnerSkillsForCourse(course.id)
                    database.skillDao().deleteSkillsForCourse(course.id)
                    val ids = database.skillDao().insertSkills(improvedSkills)
                    database.learnerSkillDao().insertLearnerSkills(ids.map { id ->
                        LearnerSkillEntity(skillId = id, courseId = course.id, attempts = 0)
                    })
                    val refreshedQuestions = improvedSkills.zip(ids).flatMap { (skill, id) ->
                        val evidence = retriever.search(skill.name, chunks, courseId = course.id, topK = 4).map { it.chunk }
                        offlineQuizGenerator.generateQuestionsForSkill(skill.copy(id = id), 2, "MEDIUM", evidence)
                    }
                    database.questionDao().insertQuestions(refreshedQuestions)
                    refreshLearningPlan(course.id)
                }
            }

            val legacyQuestions = database.questionDao().getLegacyPlaceholderQuestions(course.id)
            if (legacyQuestions.isNotEmpty()) {
                database.questionDao().deleteLegacyPlaceholderQuestions(course.id)
                val skills = database.skillDao().getSkillsSync(course.id)
                val chunks = database.documentChunkDao().getChunksSync(course.id)
                val refreshedQuestions = skills.flatMap { skill ->
                    val evidence = retriever.search(skill.name, chunks, courseId = course.id, topK = 4).map { it.chunk }
                    offlineQuizGenerator.generateQuestionsForSkill(skill, 2, "MEDIUM", evidence)
                }
                database.questionDao().insertQuestions(refreshedQuestions)
            }
        }

        val firstCourse = database.courseDao().getFirstCourse()
        if (_activeCourseId.value == null && firstCourse != null) {
            _activeCourseId.value = firstCourse.id
        }
    }

    private suspend fun removeLegacyDemoCourses() {
        val legacyDemoCourses = database.courseDao().getLegacyDemoCourses()
        for (course in legacyDemoCourses) {
            database.withTransaction {
                val documents = database.documentDao().getDocumentsSync(course.id)
                database.learningPlanDao().deletePlanItemsForCourse(course.id)
                database.learningPlanDao().deletePlansForCourse(course.id)
                database.quizAttemptDao().deleteAttemptsForCourse(course.id)
                database.questionDao().deleteQuestionsForCourse(course.id)
                database.chatMessageDao().clearMessagesForCourse(course.id)
                database.skillRelationDao().deleteRelationsForCourse(course.id)
                database.learnerSkillDao().deleteLearnerSkillsForCourse(course.id)
                database.skillDao().deleteSkillsForCourse(course.id)
                for (document in documents) {
                    database.documentChunkDao().deleteChunksForDocument(document.id)
                    database.documentDao().deleteDocument(document.id)
                }
                database.courseDao().deleteCourse(course.id)
            }
            if (_activeCourseId.value == course.id) _activeCourseId.value = null
        }
    }
}
