package com.example.ai

import com.example.ai.grounding.GroundingProvenanceValidator
import com.example.ai.local.LocalAIService
import com.example.ai.nlp.QueryUnderstanding
import com.example.ai.nlp.TutorIntent
import com.example.core.network.NetworkMonitor
import com.example.data.local.entity.DocumentChunkEntity
import com.example.data.local.entity.LearnerSkillEntity
import com.example.data.local.entity.QuestionEntity
import com.example.data.local.entity.SkillEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TutorFoundationTest {

    @Test
    fun `query understanding detects explain and extract focus terms`() {
        val result = QueryUnderstanding.analyze("Explain polymorphism in object oriented programming")
        assertEquals(TutorIntent.EXPLAIN, result.intent)
        assertTrue(result.focusTerms.contains("polymorphism"))
        assertFalse(result.isInformal)
    }

    @Test
    fun `query understanding detects simplify and informal Hindi phrasing`() {
        val result = QueryUnderstanding.analyze("bhai inheritance samjha de simple words me")
        assertEquals(TutorIntent.SIMPLIFY, result.intent)
        assertTrue(result.isInformal)
        assertTrue(result.focusTerms.contains("inheritance"))
    }

    @Test
    fun `query understanding detects hint intent`() {
        val result = QueryUnderstanding.analyze("give me a hint for solving this problem, I am stuck")
        assertEquals(TutorIntent.HINT, result.intent)
    }

    @Test
    fun `query understanding detects quiz intent`() {
        val result = QueryUnderstanding.analyze("quiz me on database indexing")
        assertEquals(TutorIntent.QUIZ, result.intent)
        assertTrue(result.focusTerms.contains("indexing") || result.focusTerms.contains("database"))
    }

    @Test
    fun `query understanding detects follow up query`() {
        val result = QueryUnderstanding.analyze("what about the second part of that explanation?")
        assertEquals(TutorIntent.FOLLOW_UP, result.intent)
        assertTrue(result.isFollowUp)
    }

    @Test
    fun `local tutor with real chunk returns grounded response and cites real document`() = runBlocking {
        val localTutor = LocalAIService()
        val chunk = DocumentChunkEntity(
            id = 101,
            documentId = 5,
            courseId = 1,
            sourceDocumentName = "Java_Basics.pdf",
            pageNumber = 17,
            chunkIndex = 2,
            text = "Inheritance enables a subclass to acquire the fields and methods of a superclass. In Java, use the extends keyword."
        )

        val skill = SkillEntity(
            id = 1,
            courseId = 1,
            name = "Inheritance",
            description = "Subclassing and code reuse",
            chapter = "OOP",
            sourceDocumentName = "Java_Basics.pdf",
            sourcePage = 17
        )

        val response = localTutor.answerTutor(
            query = "Explain inheritance",
            skill = skill,
            relevantChunks = listOf(chunk),
            courseId = 1
        )

        assertTrue(response.isOffline)
        assertTrue(response.isGroundedInMaterial)
        assertEquals("Java_Basics.pdf", response.sourceDocName)
        assertEquals(17, response.sourcePage)
        assertTrue("Response should contain chunk content", response.answer.contains("subclass") || response.answer.contains("Inheritance"))
        assertFalse("Response must NOT default to fake Physics Notes", response.sourceDocName == "Physics Notes.pdf" && chunk.sourceDocumentName != "Physics Notes.pdf")
    }

    @Test
    fun `local tutor when material is missing honestly states not found and never hallucinates citations`() = runBlocking {
        val localTutor = LocalAIService()

        val response = localTutor.answerTutor(
            query = "What is quantum entanglement?",
            skill = null,
            relevantChunks = emptyList(),
            courseId = 1
        )

        assertTrue(response.isOffline)
        assertFalse(response.isGroundedInMaterial)
        assertNull("Missing material must have null sourceDocName", response.sourceDocName)
        assertNull("Missing material must have null sourcePage", response.sourcePage)
        assertTrue("Response must explain that no readable course text was retrieved", response.answer.contains("couldn't find readable text"))
    }

    @Test
    fun `skill metadata alone never counts as retrieved evidence`() = runBlocking {
        val localTutor = LocalAIService()
        val skill = SkillEntity(
            id = 77,
            courseId = 1,
            name = "Inheritance",
            description = "A subclass inherits fields and methods from a superclass.",
            chapter = "OOP",
            sourceDocumentName = "OldNotes.pdf",
            sourcePage = 12
        )

        val response = localTutor.answerTutor(
            query = "Explain inheritance",
            skill = skill,
            relevantChunks = emptyList(),
            courseId = 1
        )

        assertFalse(response.isGroundedInMaterial)
        assertNull(response.sourceDocName)
        assertNull(response.sourcePage)
        assertTrue(response.answer.contains("couldn't find readable text"))
        assertFalse(response.answer.contains("A subclass inherits fields"))
    }

    @Test
    fun `local tutor generates honest explanation for arbitrary subject without hardcoded physics`() = runBlocking {
        val localTutor = LocalAIService()
        val chunk = DocumentChunkEntity(
            id = 202,
            documentId = 8,
            courseId = 2,
            sourceDocumentName = "Database_Systems.pdf",
            pageNumber = 42,
            chunkIndex = 1,
            text = "ACID properties ensure reliable database transactions. Atomicity guarantees that either all operations succeed or none do."
        )

        val skill = SkillEntity(
            id = 5,
            courseId = 2,
            name = "ACID Transactions",
            description = "Atomicity, Consistency, Isolation, Durability",
            chapter = "Concurrency",
            sourceDocumentName = "Database_Systems.pdf",
            sourcePage = 42
        )

        val explanation = localTutor.generateExplanation(skill, listOf(chunk))
        assertEquals("ACID Transactions", explanation.title)
        assertEquals("Database_Systems.pdf", explanation.sourceDocName)
        assertEquals(42, explanation.sourcePage)
        assertTrue(explanation.summary.contains("ACID") || explanation.summary.contains("Transactions") || explanation.summary.contains("Atomicity"))
    }

    @Test
    fun `router in offline mode strictly routes to local AI and never attempts cloud`() = runBlocking {
        val networkMonitor = NetworkMonitor(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        networkMonitor.setOfflineSimulation(true) // offline mode explicitly enabled

        var cloudWasCalled = false
        val mockCloudAI = object : com.example.ai.cloud.CloudAIService() {
            override suspend fun answerTutor(
                query: String,
                skill: SkillEntity?,
                relevantChunks: List<DocumentChunkEntity>,
                courseId: Long,
                conversationHistory: List<com.example.data.local.entity.ChatMessageEntity>
            ): TutorResponse {
                cloudWasCalled = true
                throw RuntimeException("Cloud should NOT be called in offline mode")
            }
        }

        val localAI = LocalAIService()
        val router = AIRouter(localAI, mockCloudAI, networkMonitor)

        val response = router.answerTutor(
            query = "What is a database transaction?",
            skill = null,
            relevantChunks = emptyList(),
            courseId = 1
        )

        assertFalse("Cloud service must never be called when in offline mode", cloudWasCalled)
        assertTrue("Response must indicate offline mode", response.isOffline)
        assertEquals("offline-knowledge-base", response.modelUsed)
    }

    @Test
    fun `production GroundingProvenanceValidator validates genuine chunk citation and rejects missing or disclaimed material`() {
        val chunk1 = DocumentChunkEntity(
            id = 10,
            documentId = 1,
            courseId = 1,
            sourceDocumentName = "Operating_Systems.pdf",
            pageNumber = 88,
            chunkIndex = 0,
            text = "Deadlock occurs when processes hold resources and wait for others."
        )

        // Case A: Retrieved chunk exists and model response is normal grounded text
        val normalResponse = "Deadlock is a state where concurrent processes block each other from accessing shared locks."
        val resultA = GroundingProvenanceValidator.validate(normalResponse, listOf(chunk1))
        assertTrue(resultA.isGrounded)
        assertEquals("Operating_Systems.pdf", resultA.sourceDocumentName)
        assertEquals(88, resultA.sourcePage)

        // Case B: No retrieved chunks -> grounded = false, citation = null
        val resultB = GroundingProvenanceValidator.validate(normalResponse, emptyList())
        assertFalse(resultB.isGrounded)
        assertNull(resultB.sourceDocumentName)
        assertNull(resultB.sourcePage)

        // Case C: Retrieved chunk exists but model response explicitly says material does not contain the answer
        val disclaimedResponse = "This topic wasn't found in your uploaded materials. Based on general knowledge, quantum computing uses qubits."
        val resultC = GroundingProvenanceValidator.validate(disclaimedResponse, listOf(chunk1))
        assertFalse("Must be ungrounded when model explicitly disclaims finding topic in material", resultC.isGrounded)
        assertNull(resultC.sourceDocumentName)
        assertNull(resultC.sourcePage)

        // Case D: Retrieved chunk metadata is strictly preserved
        val chunk2 = DocumentChunkEntity(
            id = 20,
            documentId = 3,
            courseId = 1,
            sourceDocumentName = "Real_Notes.pdf",
            pageNumber = 17,
            chunkIndex = 1,
            text = "Linear regression models the relationship between dependent and explanatory variables."
        )
        val resultD = GroundingProvenanceValidator.validate("Linear regression fits a best-fit line minimizing squared residuals.", listOf(chunk2))
        assertTrue(resultD.isGrounded)
        assertEquals("Real_Notes.pdf", resultD.sourceDocumentName)
        assertEquals(17, resultD.sourcePage)

        // Case E: Model response attempts to mention a different document/page in text -> citation still comes ONLY from chunk
        val hallucinatedText = "According to Physics_Hallucinated.pdf on page 999, regression is great."
        val resultE = GroundingProvenanceValidator.validate(hallucinatedText, listOf(chunk2))
        assertTrue(resultE.isGrounded)
        assertEquals("Citation document must strictly come from retrieved chunk metadata, not hallucinated text", "Real_Notes.pdf", resultE.sourceDocumentName)
        assertEquals("Citation page must strictly come from retrieved chunk metadata, not hallucinated text", 17, resultE.sourcePage)
    }

    @Test
    fun `production local AI lesson explanation does not invent fake generic formulas or examples`() = runBlocking {
        val localAI = LocalAIService()
        val skill = SkillEntity(
            id = 99,
            courseId = 1,
            name = "Recursion",
            description = "A function calling itself until reaching a base case.",
            chapter = "Algorithms",
            sourceDocumentName = "Data_Structures.pdf",
            sourcePage = 25
        )

        val chunk = DocumentChunkEntity(
            id = 1,
            documentId = 1,
            courseId = 1,
            sourceDocumentName = "Data_Structures.pdf",
            pageNumber = 25,
            chunkIndex = 0,
            text = "Recursion breaks problems into smaller subproblems. Every recursive method requires a base case to terminate."
        )

        val explanation = localAI.generateExplanation(skill, listOf(chunk))
        assertEquals("Recursion", explanation.title)
        assertEquals("Data_Structures.pdf", explanation.sourceDocName)
        assertEquals(25, explanation.sourcePage)
        assertTrue(explanation.summary.contains("Recursion"))
        // Verify no fake generic strings
        assertFalse(explanation.keyPoints.contains("Core theoretical foundation"))
        assertFalse(explanation.keyPoints.contains("Key formula / relationship"))
        assertFalse(explanation.keyPoints.contains("Application in practice"))
        assertFalse(explanation.keyPoints.contains("Boundary conditions"))
        assertFalse(explanation.examples.contains("Sample problem worked step by step."))
    }

    @Test
    fun `skill extraction returns empty list for empty or unstructured document without injecting Core Principles`() = runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.local.database.LearnMateDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val networkMonitor = NetworkMonitor(context)
        val cloudAI = com.example.ai.cloud.CloudAIService()
        val localAI = LocalAIService()
        val router = AIRouter(localAI, cloudAI, networkMonitor)
        val retriever = com.example.ai.retrieval.LocalRetriever()
        val repo = com.example.data.repository.LearnMateRepository(db, router, retriever)

        val courseId = repo.createCourse("General Biology", "Cell biology course")

        // Add document with no readable text chunks
        repo.addDocument(
            courseId = courseId,
            fileName = "empty_scan.pdf",
            fileType = "PDF",
            fileSize = "100 KB",
            extractedChunks = emptyList()
        )

        repo.processMaterialAndBuildSkillMap(courseId)

        val skills = db.skillDao().getSkillsSync(courseId)
        assertTrue("No skills should be manufactured when document has 0 text chunks", skills.isEmpty())
        assertFalse("Must NOT inject 'Core Principles'", skills.any { it.name == "Core Principles" })
        assertFalse("Must NOT inject 'Applied Analysis'", skills.any { it.name == "Applied Analysis" })

        db.close()
    }

    @Test
    fun `skill extraction accurately extracts chapters and topics from structured document`() = runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.local.database.LearnMateDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val networkMonitor = NetworkMonitor(context)
        val cloudAI = com.example.ai.cloud.CloudAIService()
        val localAI = LocalAIService()
        val router = AIRouter(localAI, cloudAI, networkMonitor)
        val retriever = com.example.ai.retrieval.LocalRetriever()
        val repo = com.example.data.repository.LearnMateRepository(db, router, retriever)

        val courseId = repo.createCourse("Data Structures", "Computer science core")

        val chunks = listOf(
            com.example.core.storage.ExtractedChunk(
                pageNumber = 1,
                chunkIndex = 0,
                text = "Chapter 1: Binary Search Trees\nBinary search trees maintain a sorted invariant where left is smaller and right is larger."
            ),
            com.example.core.storage.ExtractedChunk(
                pageNumber = 5,
                chunkIndex = 1,
                text = "Chapter 2: Red-Black Trees\nRed-black trees are self-balancing binary search trees ensuring logarithmic search time."
            )
        )

        repo.addDocument(
            courseId = courseId,
            fileName = "Algorithms.pdf",
            fileType = "PDF",
            fileSize = "2.1 MB",
            extractedChunks = chunks
        )

        repo.processMaterialAndBuildSkillMap(courseId)

        val skills = db.skillDao().getSkillsSync(courseId)
        assertEquals(2, skills.size)
        assertTrue(skills.any { it.name.contains("Binary Search Trees") })
        assertTrue(skills.any { it.name.contains("Red-Black Trees") })

        db.close()
    }

    @Test
    fun `skill extraction recognizes ordinary PDF headings and does not invent prerequisite links`() = runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.local.database.LearnMateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val router = AIRouter(LocalAIService(), com.example.ai.cloud.CloudAIService(), NetworkMonitor(context))
        val repo = com.example.data.repository.LearnMateRepository(db, router, com.example.ai.retrieval.LocalRetriever())
        val courseId = repo.createCourse("Biology", "Photosynthesis")
        repo.addDocument(
            courseId = courseId,
            fileName = "Biology.pdf",
            fileType = "PDF",
            fileSize = "3 KB",
            extractedChunks = listOf(
                com.example.core.storage.ExtractedChunk(
                    pageNumber = 1,
                    chunkIndex = 0,
                    text = listOf(
                        "LearnMate device test material", "BIOLOGY STUDY SHEET | UNIT 3", "Photosynthesis",
                        "A concise guide to how plants convert light energy into stored chemical energy.",
                        "Where the process happens", "Photosynthesis takes place in chloroplasts and chlorophyll absorbs light.",
                        "Two linked stages", "Light-dependent reactions release oxygen and form ATP and NADPH.",
                        "What changes the rate?", "Light intensity and carbon dioxide concentration can change the rate of photosynthesis."
                    ).joinToString("\n")
                )
            )
        )

        repo.processMaterialAndBuildSkillMap(courseId)

        val skills = db.skillDao().getSkillsSync(courseId)
        assertTrue(skills.map { it.name }.containsAll(listOf("Photosynthesis", "Where the process happens", "Two linked stages", "What changes the rate?")))
        assertTrue(db.skillRelationDao().getRelationsSync(courseId).isEmpty())
        db.close()
    }

    @Test
    fun `untouched legacy course map and placeholder quiz are refreshed without discarding course content`() = runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.local.database.LearnMateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = com.example.data.repository.LearnMateRepository(
            db,
            AIRouter(LocalAIService(), com.example.ai.cloud.CloudAIService(), NetworkMonitor(context)),
            com.example.ai.retrieval.LocalRetriever()
        )
        val courseId = repo.createCourse("Biology", "Photosynthesis")
        val documentId = repo.addDocument(
            courseId, "Biology.pdf", "PDF", "3 KB",
            listOf(com.example.core.storage.ExtractedChunk(1, 0, listOf(
                "LearnMate device test material", "Photosynthesis",
                "A concise guide to how plants convert light energy into stored chemical energy.",
                "Where the process happens", "Photosynthesis takes place in chloroplasts and chlorophyll absorbs light.",
                "Two linked stages", "Light-dependent reactions release oxygen and form ATP and NADPH.",
                "What changes the rate?", "Light intensity and carbon dioxide concentration can change the rate of photosynthesis."
            ).joinToString("\n")))
        )
        val legacySkillId = db.skillDao().insertSkill(SkillEntity(
            courseId = courseId,
            name = "LearnMate device test",
            description = "LearnMate device test material - educational content for extraction checks.",
            chapter = "Core Concepts",
            sourceDocumentId = documentId,
            sourceDocumentName = "Biology.pdf"
        ))
        db.learnerSkillDao().insertLearnerSkills(listOf(LearnerSkillEntity(legacySkillId, courseId)))
        db.questionDao().insertQuestions(listOf(QuestionEntity(
            courseId = courseId,
            skillId = legacySkillId,
            questionText = "Which statement is supported?",
            optionA = "This detail is not stated in the retrieved passage.",
            optionB = "This statement is unrelated to the retrieved topic.",
            optionC = "Photosynthesis occurs in chloroplasts.",
            optionD = "This claim is not supported by the retrieved passage.",
            correctAnswerIndex = 2,
            explanation = "Old generated item",
            sourceDocumentName = "Biology.pdf"
        )))

        repo.ensureInitialData()

        val refreshedSkills = db.skillDao().getSkillsSync(courseId)
        val refreshedQuestions = db.questionDao().getQuestionsSync(courseId)
        assertTrue(refreshedSkills.map { it.name }.containsAll(listOf("Photosynthesis", "Where the process happens", "Two linked stages", "What changes the rate?")))
        assertTrue(refreshedQuestions.isNotEmpty())
        assertTrue(refreshedQuestions.none { it.optionA.contains("not stated in the retrieved passage") || it.optionB.contains("not stated in the retrieved passage") })
        assertTrue(refreshedQuestions.all { it.optionC.isBlank() && it.optionD.isBlank() })
        assertEquals(0, db.learnerSkillDao().getLearnerSkillsSync(courseId).sumOf { it.attempts })
        assertTrue(db.documentChunkDao().getChunksSync(courseId).isNotEmpty())
        db.close()
    }

    @Test
    fun `local generateExplanation with zero retrieved chunks returns honest null citation state and no fabricated points`() = runBlocking {
        val localAI = LocalAIService()
        val skill = SkillEntity(
            id = 101,
            courseId = 1,
            name = "Quantum Physics",
            description = "Quantum mechanics and wave functions",
            chapter = "Physics",
            sourceDocumentName = "Fallback.pdf",
            sourcePage = 10
        )

        val explanation = localAI.generateExplanation(skill, emptyList())
        assertTrue(explanation.summary.contains("No readable course material was available"))
        assertTrue(explanation.keyPoints.isEmpty())
        assertTrue(explanation.examples.isEmpty())
        assertNull(explanation.sourceDocName)
        assertNull(explanation.sourcePage)
    }

    @Test
    fun `cloud generateExplanation with zero retrieved chunks returns honest null citation state and no fabricated points`() = runBlocking {
        val cloudAI = object : com.example.ai.cloud.CloudAIService() {
            override fun isConfigured(): Boolean = true
        }

        val skill = SkillEntity(
            id = 102,
            courseId = 1,
            name = "Astrophysics",
            description = "Stellar evolution",
            chapter = "Astronomy",
            sourceDocumentName = "Fallback.pdf",
            sourcePage = 5
        )

        val explanation = cloudAI.generateExplanation(skill, emptyList())
        assertTrue(explanation.summary.contains("No readable course material was available"))
        assertTrue(explanation.keyPoints.isEmpty())
        assertTrue(explanation.examples.isEmpty())
        assertNull(explanation.sourceDocName)
        assertNull(explanation.sourcePage)
    }

    @Test
    fun `local generateQuestionsForSkill correct answer is deterministically varying and valid`() = runBlocking {
        val localAI = LocalAIService()
        val chunk1 = DocumentChunkEntity(
            id = 1001,
            documentId = 5,
            courseId = 1,
            sourceDocumentName = "Java_Basics.pdf",
            pageNumber = 17,
            chunkIndex = 2,
            text = "Inheritance is a key mechanism of OOP."
        )
        val skill1 = SkillEntity(
            id = 5001,
            courseId = 1,
            name = "Inheritance",
            description = "Subclassing and code reuse",
            chapter = "OOP"
        )

        val correctIndices = mutableSetOf<Int>()
        
        for (i in 0..15) {
            val skill = skill1.copy(id = 5001L + i)
            val chunk = chunk1.copy(id = 1001L + i * 17)
            val difficulty = if (i % 2 == 0) "MEDIUM" else "HARD"
            
            val questions = localAI.generateQuestionsForSkill(skill, 1, difficulty, listOf(chunk))
            assertFalse(questions.isEmpty())
            val q = questions.first()
            
            val correctIdx = q.correctAnswerIndex
            println("DEBUG: i=$i, correctIdx=$correctIdx, skill.id=${skill.id}, chunk.id=${chunk.id}, difficulty=$difficulty")
            assertTrue("Correct answer index must be in range 0..1", correctIdx in 0..1)
            correctIndices.add(correctIdx)
            
            val chosenText = when (correctIdx) {
                0 -> q.optionA
                1 -> q.optionB
                2 -> q.optionC
                3 -> q.optionD
                else -> ""
            }
            assertEquals("Yes — this fact appears in your notes", chosenText)
            
            assertTrue(q.questionText.contains("Inheritance is a key mechanism of OOP."))
            val allOptions = listOf(q.optionA, q.optionB)
            val distractors = allOptions.toMutableList().apply { removeAt(correctIdx) }
            for (distractor in distractors) {
                assertFalse(distractor == chosenText)
                assertTrue(distractor.isNotBlank())
            }
        }
        
        assertTrue("Correct answer index should vary across different questions", correctIndices.size > 1)
    }

    @Test
    fun `local generateQuestionsForSkill repeated generation is deterministic`() = runBlocking {
        val localAI = LocalAIService()
        val chunk = DocumentChunkEntity(
            id = 1001,
            documentId = 5,
            courseId = 1,
            sourceDocumentName = "Java_Basics.pdf",
            pageNumber = 17,
            chunkIndex = 2,
            text = "Polymorphism allows dynamic method dispatch."
        )
        val skill = SkillEntity(
            id = 5001,
            courseId = 1,
            name = "Polymorphism",
            description = "Dynamic method dispatch and polymorphism",
            chapter = "OOP"
        )

        val questions1 = localAI.generateQuestionsForSkill(skill, 1, "MEDIUM", listOf(chunk))
        val questions2 = localAI.generateQuestionsForSkill(skill, 1, "MEDIUM", listOf(chunk))

        assertEquals(questions1.size, questions2.size)
        val q1 = questions1.first()
        val q2 = questions2.first()

        assertEquals(q1.correctAnswerIndex, q2.correctAnswerIndex)
        assertEquals(q1.optionA, q2.optionA)
        assertEquals(q1.optionB, q2.optionB)
        assertEquals(q1.optionC, q2.optionC)
        assertEquals(q1.optionD, q2.optionD)
    }
    @Test
    fun `local quiz returns distinct questions grounded in available source statements`() = runBlocking {
        val localAI = LocalAIService()
        val chunk = DocumentChunkEntity(
            id = 701,
            documentId = 9,
            courseId = 3,
            sourceDocumentName = "OOP_Notes.pdf",
            pageNumber = 12,
            chunkIndex = 0,
            text = "Inheritance allows code reuse. Subclasses inherit fields and methods. Java uses the extends keyword for class inheritance."
        )
        val skill = SkillEntity(
            id = 81,
            courseId = 3,
            name = "Inheritance",
            description = "Inheritance in object-oriented programming",
            chapter = "OOP"
        )

        val questions = localAI.generateQuestionsForSkill(skill, 2, "MEDIUM", listOf(chunk))

        assertEquals(2, questions.size)
        assertTrue(questions.all { it.sourceDocumentName == "OOP_Notes.pdf" && it.sourcePage == 12 })
        val correctAnswers = questions.map { question ->
            listOf(question.optionA, question.optionB, question.optionC, question.optionD)[question.correctAnswerIndex]
        }
        assertEquals(1, correctAnswers.distinct().size)
        assertEquals("Yes — this fact appears in your notes", correctAnswers.first())
        assertEquals(2, questions.map { it.questionText }.distinct().size)
        assertTrue(questions.all { it.optionC.isBlank() && it.optionD.isBlank() })
    }

}
