package com.example.ai

import com.example.ai.grounding.GroundingProvenanceValidator
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
}
