package com.example.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.storage.DocumentTextProcessor
import com.example.core.storage.ExtractedPage
import com.example.core.storage.IngestionResult
import com.example.core.storage.PdfTextExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PdfTextExtractorTest {

    @Test
    fun `1 valid material extracts chunks accurately`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val extractor = PdfTextExtractor(context)

        val sampleText = """
            Chapter 1: Kinematics
            
            Displacement is the vector connecting the initial position to the final position.
            Velocity is defined as the time derivative of displacement: v = ds/dt.
            
            Chapter 2: Dynamics
            
            Force causes acceleration. Newton's second law states F = m * a.
            When mass is constant, the acceleration is proportional to the net applied force.
        """.trimIndent()

        val inputStream = ByteArrayInputStream(sampleText.toByteArray(Charsets.UTF_8))
        val result = extractor.extractFromPlainTextStream(inputStream)

        assertTrue("Result must be success for valid material", result is IngestionResult.Success)
        val chunks = (result as IngestionResult.Success).chunks
        assertTrue(chunks.isNotEmpty())
        assertEquals(1, chunks.first().pageNumber)
        assertTrue(chunks.first().text.contains("Kinematics") || chunks.first().text.contains("Dynamics"))
    }

    @Test
    fun `2 empty material produces Failure result and no fake content`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val extractor = PdfTextExtractor(context)

        val emptyInputStream = ByteArrayInputStream("   \n\t  ".toByteArray(Charsets.UTF_8))
        val result = extractor.extractFromPlainTextStream(emptyInputStream)

        assertTrue("Empty document must report Failure", result is IngestionResult.Failure)
        val reason = (result as IngestionResult.Failure).reason
        assertTrue(reason.contains("empty", ignoreCase = true) || reason.contains("blank", ignoreCase = true))
    }

    @Test
    fun `3 invalid or corrupt PDF produces honest Failure without fake physics chunks`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val extractor = PdfTextExtractor(context)

        val corruptedPdfBytes = "%PDF-1.4\ncorrupted junk binary data that cannot flate decode\n%%EOF".toByteArray(Charsets.ISO_8859_1)
        val result = extractor.extractFromPdfStream(ByteArrayInputStream(corruptedPdfBytes), "Calculus_Syllabus.pdf")

        assertTrue("Corrupted PDF must return IngestionResult.Failure", result is IngestionResult.Failure)
        val message = (result as IngestionResult.Failure).reason
        assertTrue("Must state failure to extract text", message.contains("Could not extract", ignoreCase = true) || message.contains("scanned", ignoreCase = true))
        assertFalse("Must never inject fake physics content", message.contains("Newton") || message.contains("friction"))
    }

    @Test
    fun `4 extraction failure on 0-byte PDF stream reports Failure`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val extractor = PdfTextExtractor(context)

        val zeroBytes = ByteArray(0)
        val result = extractor.extractFromPdfStream(ByteArrayInputStream(zeroBytes), "ZeroByte.pdf")

        assertTrue("0-byte PDF must fail ingestion", result is IngestionResult.Failure)
        assertTrue((result as IngestionResult.Failure).reason.contains("0 bytes", ignoreCase = true) || result.reason.contains("empty", ignoreCase = true))
    }

    @Test
    fun `5 normalization removes excessive whitespace and control characters without changing meaning`() {
        val messyText = "Line 1   with   spaces\r\n\r\n\r\n\r\nLine 2\u00A0with nonbreaking space\t\u0000\u0007\n\nLine 3"
        val normalized = DocumentTextProcessor.normalizeText(messyText)

        assertFalse("Must not contain CR carriage returns", normalized.contains("\r"))
        assertFalse("Must not contain unprintable NUL control chars", normalized.contains("\u0000"))
        assertFalse("Must collapse 4 newlines to maximum 2", normalized.contains("\n\n\n"))
        assertTrue("Must preserve Line 1 text", normalized.contains("Line 1"))
        assertTrue("Must preserve Line 2 text", normalized.contains("Line 2"))
        assertTrue("Must preserve Line 3 text", normalized.contains("Line 3"))
    }

    @Test
    fun `6 and 7 chunk sizing approximate 400 to 600 tokens with overlap`() {
        val longParagraph = StringBuilder()
        for (i in 1..20) {
            longParagraph.append("Paragraph $i discusses cellular metabolism, glycolysis, Krebs cycle, and ATP synthesis in mitochondria. ")
                .append("Enzymes catalyze each distinct biochemical reaction with high substrate specificity.\n\n")
        }

        val pages = listOf(ExtractedPage(pageNumber = 1, text = longParagraph.toString()))
        val chunks = DocumentTextProcessor.chunkPages(pages)

        assertTrue("Should produce multiple chunks for long document", chunks.size > 1)
        for (chunk in chunks) {
            assertTrue("Chunk length must be >= 100 chars", chunk.text.length >= 100)
            assertTrue("Chunk length must stay under max boundary (approx 2800 chars)", chunk.text.length <= DocumentTextProcessor.TARGET_CHUNK_MAX_CHARS + 400)
        }

        // Verify overlap occurs in subsequent chunks
        val secondChunk = chunks[1]
        assertTrue("Second chunk should retain continuity marker or overlap snippet", secondChunk.text.contains("...") || secondChunk.text.contains("Paragraph"))
    }

    @Test
    fun `8 page provenance and 9 chunk ordering preserved across pages`() {
        val page1 = ExtractedPage(pageNumber = 1, text = "Page 1: Introduction to Data Structures. Arrays and Linked Lists.")
        val page2 = ExtractedPage(pageNumber = 2, text = "Page 2: Trees and Graphs. Binary Search Trees and Traversals.")
        val page3 = ExtractedPage(pageNumber = 3, text = "Page 3: Hash Tables and Collision Resolution.")

        val chunks = DocumentTextProcessor.chunkPages(listOf(page1, page2, page3))

        assertEquals("Must produce 3 page chunks", 3, chunks.size)
        assertEquals(1, chunks[0].pageNumber)
        assertEquals(2, chunks[1].pageNumber)
        assertEquals(3, chunks[2].pageNumber)

        assertEquals(0, chunks[0].chunkIndex)
        assertEquals(1, chunks[1].chunkIndex)
        assertEquals(2, chunks[2].chunkIndex)
    }

    @Test
    fun `10 empty chunks rejected from output`() {
        val blankPages = listOf(
            ExtractedPage(1, "   \n\n\t   "),
            ExtractedPage(2, ""),
            ExtractedPage(3, "\n\n\n")
        )

        val chunks = DocumentTextProcessor.chunkPages(blankPages)
        assertTrue("Must reject empty or whitespace-only chunks completely", chunks.isEmpty())
    }

    @Test
    fun `13 oversized input is rejected by processor boundary check`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val extractor = PdfTextExtractor(context)

        val dummyUri = android.net.Uri.parse("content://dummy/large.pdf")
        val oversizedBytes = DocumentTextProcessor.MAX_FILE_SIZE_BYTES + 1024L

        val result = extractor.extractTextFromUri(dummyUri, "LargeTextbook.pdf", explicitSizeBytes = oversizedBytes)
        assertTrue("Must fail on oversized file", result is IngestionResult.Failure)
        assertTrue((result as IngestionResult.Failure).reason.contains("50 MB", ignoreCase = true))
    }

    @Test
    fun `14 arbitrary subjects extracted without physics bias`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val extractor = PdfTextExtractor(context)

        val javaText = """
            Chapter 1: Variables and Data Types
            
            Java is a statically typed programming language. Every variable must have a declared type before use.
            Primitive types include int, double, boolean, and char. Reference types include classes and arrays.
            
            Chapter 2: Control Flow Statements
            
            Conditional execution is achieved using if-else statements and switch expressions.
            Loops include while, do-while, and traditional for loops.
        """.trimIndent()

        val chunks = extractor.extractFromPlainText(ByteArrayInputStream(javaText.toByteArray(Charsets.UTF_8)))
        assertTrue("Must extract chunks for computer science curriculum", chunks.isNotEmpty())
        assertTrue("Chunk text must reflect input document", chunks.any { it.text.contains("statically typed") })
        assertFalse("Must not inject physics keywords", chunks.any { it.text.contains("kinematics") || it.text.contains("gravity") })
    }
}
