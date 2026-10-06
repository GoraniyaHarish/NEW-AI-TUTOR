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
}
