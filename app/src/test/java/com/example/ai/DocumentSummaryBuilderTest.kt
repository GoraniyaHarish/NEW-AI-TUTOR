package com.example.ai

import com.example.ai.local.LocalAIService
import com.example.ai.nlp.QueryUnderstanding
import com.example.ai.nlp.TutorIntent
import com.example.ai.summarization.DocumentSummaryBuilder
import com.example.data.local.entity.DocumentChunkEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentSummaryBuilderTest {
    private fun chunk(id: Long, page: Int, text: String) = DocumentChunkEntity(
        id = id,
        documentId = 7,
        courseId = 3,
        sourceDocumentName = "Biology.pdf",
        pageNumber = page,
        text = text,
        chunkIndex = id.toInt()
    )

    @Test
    fun `British spelling and common typo are understood as summary requests`() {
        assertEquals(TutorIntent.SUMMARY, QueryUnderstanding.analyze("summarise my document").intent)
        assertEquals(TutorIntent.SUMMARY, QueryUnderstanding.analyze("summerise this file").intent)
    }

    @Test
    fun `offline summary quotes source sentences and preserves page provenance`() = runBlocking {
        val source = chunk(
            1,
            4,
            "Photosynthesis converts light energy into chemical energy stored in glucose. Chlorophyll absorbs light inside chloroplasts."
        )

        val answer = LocalAIService().answerTutor("summarise the document", null, listOf(source), 3)

        assertTrue(answer.isGroundedInMaterial)
        assertTrue(answer.answer.contains("Photosynthesis converts light energy"))
        assertTrue(answer.answer.contains("page 4"))
        assertFalse(answer.answer.contains("Newton"))
    }

    @Test
    fun `context budget truncates a single oversized chunk and reports partial coverage`() {
        val oversized = chunk(1, 1, "A".repeat(200))

        val selected = DocumentSummaryBuilder.selectContext(listOf(oversized), maxCharacters = 64)

        assertFalse(selected.isComplete)
        assertTrue(selected.chunks.sumOf { it.text.length } <= 64)
        assertTrue(selected.originalCharacterCount == 200)
    }
}
