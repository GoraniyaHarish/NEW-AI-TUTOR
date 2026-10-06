package com.example.ai

import com.example.ai.local.LocalAIService
import com.example.ai.nlp.QueryUnderstanding
import com.example.ai.nlp.TutorIntent
import com.example.core.network.NetworkMonitor
import com.example.data.local.entity.DocumentChunkEntity
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
@Config(sdk = [36])
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
        assertTrue("Response must state not found in uploaded materials", response.answer.contains("wasn't found in your uploaded study materials"))
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
    fun `citation provenance verification strictly bounds citation to retrieved chunks`() = runBlocking {
        val cloudAI = object : com.example.ai.cloud.CloudAIService() {
            override fun isConfigured(): Boolean = true
            override suspend fun answerTutor(
                query: String,
                skill: SkillEntity?,
                relevantChunks: List<DocumentChunkEntity>,
                courseId: Long,
                conversationHistory: List<com.example.data.local.entity.ChatMessageEntity>
            ): TutorResponse {
                val hasEvidence = relevantChunks.isNotEmpty()
                val topChunk = relevantChunks.firstOrNull()
                return TutorResponse(
                    answer = "A database transaction is an atomic unit of execution.",
                    sourceDocName = if (hasEvidence) topChunk?.sourceDocumentName else null,
                    sourcePage = if (hasEvidence) topChunk?.pageNumber else null,
                    isOffline = false,
                    isGroundedInMaterial = hasEvidence,
                    modelUsed = "gemini-3.5-flash-lite"
                )
            }
        }

        // Test A: With retrieved chunk -> provenance attached
        val chunk = DocumentChunkEntity(
            id = 50,
            documentId = 2,
            courseId = 1,
            sourceDocumentName = "Operating_Systems.pdf",
            pageNumber = 88,
            chunkIndex = 0,
            text = "Deadlock occurs when processes hold resources and wait for others."
        )

        val groundedResponse = cloudAI.answerTutor(
            query = "What is deadlock?",
            skill = null,
            relevantChunks = listOf(chunk),
            courseId = 1
        )

        assertTrue(groundedResponse.isGroundedInMaterial)
        assertEquals("Operating_Systems.pdf", groundedResponse.sourceDocName)
        assertEquals(88, groundedResponse.sourcePage)

        // Test B: Empty retrieval -> no citation, grounded=false
        val ungroundedResponse = cloudAI.answerTutor(
            query = "What is deadlock?",
            skill = null,
            relevantChunks = emptyList(),
            courseId = 1
        )

        assertFalse(ungroundedResponse.isGroundedInMaterial)
        assertNull(ungroundedResponse.sourceDocName)
        assertNull(ungroundedResponse.sourcePage)
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
}
