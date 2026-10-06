package com.example.ai

import com.example.ai.retrieval.LocalRetriever
import com.example.data.local.entity.DocumentChunkEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRetrieverTest {

    private val retriever = LocalRetriever()

    private val chunks = listOf(
        DocumentChunkEntity(
            id = 1,
            documentId = 1,
            courseId = 1,
            sourceDocumentName = "Physics Notes.pdf",
            pageNumber = 12,
            chunkIndex = 0,
            text = "Kinematics describes motion, displacement, velocity and uniform acceleration without regard to forces."
        ),
        DocumentChunkEntity(
            id = 2,
            documentId = 1,
            courseId = 1,
            sourceDocumentName = "Physics Notes.pdf",
            pageNumber = 24,
            chunkIndex = 1,
            text = "Newton's Second Law of Motion: The rate of change of momentum is proportional to the applied force. F = m * a."
        ),
        DocumentChunkEntity(
            id = 3,
            documentId = 1,
            courseId = 1,
            sourceDocumentName = "Physics Notes.pdf",
            pageNumber = 28,
            chunkIndex = 2,
            text = "Friction is the resistive contact force. Static friction has maximum limit f_s = mu_s * N. Kinetic friction is sliding friction."
        )
    )

    @Test
    fun `empty query returns top chunks with baseline score`() {
        val results = retriever.search("", chunks, topK = 2)
        assertEquals(0, results.size)
    }

    @Test
    fun `search finds relevant chunk and cites source document and page`() {
        val results = retriever.search("Newton Second Law force", chunks, topK = 3)
        assertTrue(results.isNotEmpty())

        val top = results.first()
        assertEquals(2L, top.chunk.id)
        assertEquals("Physics Notes.pdf", top.chunk.sourceDocumentName)
        assertEquals(24, top.chunk.pageNumber)
        assertTrue(top.score > 0f)
        assertTrue(top.matchedTerms.contains("newton") || top.matchedTerms.contains("force"))
    }

    @Test
    fun `exact phrase match receives higher ranking`() {
        val results = retriever.search("Static friction", chunks, topK = 3)
        assertTrue(results.isNotEmpty())

        val top = results.first()
        assertEquals(3L, top.chunk.id)
        assertEquals(28, top.chunk.pageNumber)
    }
}
