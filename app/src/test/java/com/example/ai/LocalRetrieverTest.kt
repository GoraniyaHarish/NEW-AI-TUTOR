package com.example.ai

import com.example.ai.retrieval.LocalRetriever
import com.example.ai.retrieval.QueryNormalizer
import com.example.data.local.entity.DocumentChunkEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalRetrieverTest {

    private val retriever = LocalRetriever()

    // Domain-neutral educational test fixtures across distinct courses and documents
    private val csCourseChunks = listOf(
        DocumentChunkEntity(
            id = 101,
            documentId = 1,
            courseId = 10,
            sourceDocumentName = "Algorithms_DataStructures.pdf",
            pageNumber = 14,
            chunkIndex = 0,
            text = "Recursion is a method of solving computational problems where a function calls itself directly or indirectly to solve smaller subproblems with a base case."
        ),
        DocumentChunkEntity(
            id = 102,
            documentId = 1,
            courseId = 10,
            sourceDocumentName = "Algorithms_DataStructures.pdf",
            pageNumber = 22,
            chunkIndex = 1,
            text = "Binary Search Tree (BST) is a node-based binary tree data structure which has the property that the left subtree of a node contains only keys lesser than the node's key."
        ),
        DocumentChunkEntity(
            id = 103,
            documentId = 2,
            courseId = 10,
            sourceDocumentName = "Operating_Systems.pdf",
            pageNumber = 45,
            chunkIndex = 0,
            text = "Deadlock occurs in operating systems when four Coffman conditions hold simultaneously: mutual exclusion, hold and wait, no preemption, and circular wait."
        ),
        DocumentChunkEntity(
            id = 104,
            documentId = 2,
            courseId = 10,
            sourceDocumentName = "Operating_Systems.pdf",
            pageNumber = 48,
            chunkIndex = 1,
            text = "Virtual memory management uses paging and page replacement algorithms like LRU (Least Recently Used) to manage RAM efficiently."
        )
    )

    private val bioCourseChunks = listOf(
        DocumentChunkEntity(
            id = 201,
            documentId = 5,
            courseId = 20,
            sourceDocumentName = "Cellular_Biology.pdf",
            pageNumber = 8,
            chunkIndex = 0,
            text = "Mitochondria are membrane-bound cell organelles that generate most of the chemical energy needed to power the biochemical reactions via ATP synthesis."
        ),
        DocumentChunkEntity(
            id = 202,
            documentId = 5,
            courseId = 20,
            sourceDocumentName = "Cellular_Biology.pdf",
            pageNumber = 19,
            chunkIndex = 1,
            text = "Photosynthesis is the biological process used by plants and other organisms to convert light energy into chemical energy stored in glucose molecules."
        )
    )

    private val allCorpusChunks = csCourseChunks + bioCourseChunks

    @Test
    fun `1 exact term match ranks relevant chunk highly`() {
        val results = retriever.search("deadlock Coffman conditions", allCorpusChunks, courseId = 10)
        assertTrue("Results should not be empty for exact keywords", results.isNotEmpty())

        val top = results.first()
        assertEquals(103L, top.chunk.id)
        assertEquals("Operating_Systems.pdf", top.documentName)
        assertEquals(45, top.pageNumber)
        assertTrue(top.matchedTerms.contains("deadlock") || top.matchedTerms.contains("coffman"))
    }

    @Test
    fun `2 natural-language query variations retrieve the same relevant chunk`() {
        val queryVariants = listOf(
            "What is recursion?",
            "can you explain recursion",
            "I don't understand recursion",
            "please teach me about recursion",
            "how does recursion work",
            "give me an example of recursion"
        )

        for (query in queryVariants) {
            val results = retriever.search(query, allCorpusChunks, courseId = 10)
            assertTrue("Query '$query' must retrieve recursion chunk", results.isNotEmpty())
            assertEquals("Top chunk for '$query' must be recursion chunk", 101L, results.first().chunk.id)
            assertEquals("Algorithms_DataStructures.pdf", results.first().documentName)
            assertEquals(14, results.first().pageNumber)
        }
    }

    @Test
    fun `3 irrelevant chunk ranks lower or is excluded`() {
        val results = retriever.search("Binary Search Tree keys", csCourseChunks, courseId = 10)
        assertTrue(results.isNotEmpty())

        val top = results.first()
        assertEquals(102L, top.chunk.id) // BST chunk

        // Verify other chunks like Deadlock rank significantly lower or are filtered out
        val deadlockResult = results.firstOrNull { it.chunk.id == 103L }
        assertTrue(deadlockResult == null || deadlockResult.score < top.score)
    }

    @Test
    fun `4 no relevant result returns empty without fabricating fallback`() {
        val results = retriever.search("quantum gravity black hole singularity", csCourseChunks, courseId = 10)
        assertTrue("Completely irrelevant query must return empty results", results.isEmpty())
    }

    @Test
    fun `5 top-K limit works strictly`() {
        val resultsK1 = retriever.search("algorithms data structures computer science", csCourseChunks, topK = 1)
        assertTrue(resultsK1.size <= 1)

        val resultsK2 = retriever.search("algorithms data structures computer science", csCourseChunks, topK = 2)
        assertTrue(resultsK2.size <= 2)
    }

    @Test
    fun `6 relevance threshold filters out weak spurious matches`() {
        val highThresholdRetriever = LocalRetriever(minRelevanceThreshold = 5.0f)
        val results = highThresholdRetriever.search("recursion", csCourseChunks)
        // Only chunks with strong match and bonuses will pass high threshold
        for (r in results) {
            assertTrue(r.score >= 5.0f)
        }
    }

    @Test
    fun `7 course isolation strictly prevents cross-course retrieval leakage`() {
        // Querying for biology topic inside CS course
        val csResults = retriever.search("Mitochondria ATP synthesis", allCorpusChunks, courseId = 10)
        assertTrue("CS Course 10 must NEVER return Biology chunks", csResults.isEmpty())

        // Querying for biology topic inside Biology course
        val bioResults = retriever.search("Mitochondria ATP synthesis", allCorpusChunks, courseId = 20)
        assertTrue("Biology Course 20 must return Mitochondria chunk", bioResults.isNotEmpty())
        assertEquals(201L, bioResults.first().chunk.id)
        assertEquals("Cellular_Biology.pdf", bioResults.first().documentName)
    }

    @Test
    fun `8 document provenance is preserved accurately`() {
        val results = retriever.search("Virtual memory paging LRU", allCorpusChunks, courseId = 10)
        assertTrue(results.isNotEmpty())

        val top = results.first()
        assertEquals(2L, top.documentId)
        assertEquals("Operating_Systems.pdf", top.documentName)
        assertFalse("Must not inject fake 'Course Notes'", top.documentName == "Course Notes")
    }

    @Test
    fun `9 page number is preserved accurately`() {
        val results = retriever.search("LRU Least Recently Used", allCorpusChunks, courseId = 10)
        assertTrue(results.isNotEmpty())
        assertEquals(48, results.first().pageNumber)
    }

    @Test
    fun `10 chunk index is preserved accurately`() {
        val results = retriever.search("Virtual memory", allCorpusChunks, courseId = 10)
        assertTrue(results.isNotEmpty())
        assertEquals(1, results.first().chunkIndex)
    }

    @Test
    fun `11 empty query returns empty list`() {
        val results = retriever.search("", csCourseChunks)
        assertTrue("Empty query must return empty list", results.isEmpty())
    }

    @Test
    fun `12 whitespace-only query returns empty list`() {
        val results = retriever.search("    \n\t   ", csCourseChunks)
        assertTrue("Whitespace-only query must return empty list", results.isEmpty())
    }

    @Test
    fun `13 punctuation-only query returns empty list`() {
        val results = retriever.search("???? ... !!! -- ,,,", csCourseChunks)
        assertTrue("Punctuation-only query must return empty list", results.isEmpty())
    }

    @Test
    fun `14 unusual Unicode Greek math characters do not crash retrieval`() {
        val mathChunk = DocumentChunkEntity(
            id = 301,
            documentId = 7,
            courseId = 30,
            sourceDocumentName = "Calculus_Notes.pdf",
            pageNumber = 3,
            chunkIndex = 0,
            text = "The derivative is defined as limit as \u0394x -> 0 of (f(x + \u0394x) - f(x)) / \u0394x. Integration \u222B f(x) dx is the inverse operation."
        )

        val results = retriever.search("derivative \u0394x integration \u222B", listOf(mathChunk), courseId = 30)
        assertTrue("Must retrieve math chunk with unicode symbols", results.isNotEmpty())
        assertEquals(301L, results.first().chunk.id)
    }

    @Test
    fun `15 duplicate chunks behave deterministically with stable tie-breaking`() {
        val dup1 = DocumentChunkEntity(
            id = 401,
            documentId = 9,
            courseId = 40,
            sourceDocumentName = "History.pdf",
            pageNumber = 10,
            chunkIndex = 0,
            text = "The Industrial Revolution began in Great Britain in the late 18th century."
        )
        val dup2 = DocumentChunkEntity(
            id = 402,
            documentId = 9,
            courseId = 40,
            sourceDocumentName = "History.pdf",
            pageNumber = 10,
            chunkIndex = 1,
            text = "The Industrial Revolution began in Great Britain in the late 18th century."
        )

        val results = retriever.search("Industrial Revolution Great Britain", listOf(dup1, dup2), courseId = 40)
        assertEquals(2, results.size)
        assertEquals(401L, results[0].chunk.id)
        assertEquals(402L, results[1].chunk.id)
    }

    @Test
    fun `16 repeated identical query produces identical deterministic ranking`() {
        val run1 = retriever.search("operating systems deadlock", csCourseChunks, courseId = 10)
        val run2 = retriever.search("operating systems deadlock", csCourseChunks, courseId = 10)

        assertEquals(run1.size, run2.size)
        for (i in run1.indices) {
            assertEquals(run1[i].chunk.id, run2[i].chunk.id)
            assertEquals(run1[i].score, run2[i].score, 0.0001f)
        }
    }

    @Test
    fun `17 retrieval works completely offline without network`() {
        // Pure JVM execution with zero network calls
        val results = retriever.search("photosynthesis glucose", bioCourseChunks, courseId = 20)
        assertTrue(results.isNotEmpty())
        assertEquals(202L, results.first().chunk.id)
        assertEquals("Cellular_Biology.pdf", results.first().documentName)
        assertEquals(19, results.first().pageNumber)
    }

    @Test
    fun `18 retrieval works gracefully when course has no chunks`() {
        val results = retriever.search("recursion", emptyList(), courseId = 99)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `19 query normalizer handles stemming correctly`() {
        assertEquals("algorithm", QueryNormalizer.normalizeStem("algorithms"))
        assertEquals("matrix", QueryNormalizer.normalizeStem("matrices"))
        assertEquals("process", QueryNormalizer.normalizeStem("processes"))
        assertEquals("run", QueryNormalizer.normalizeStem("running"))
        assertEquals("connect", QueryNormalizer.normalizeStem("connected"))
    }
}
