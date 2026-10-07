package com.example.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.storage.PdfTextExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PdfTextExtractorTest {

    @Test
    fun `extractFromPlainText extracts and chunks content accurately`() {
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
        val chunks = extractor.extractFromPlainText(inputStream)

        assertTrue(chunks.isNotEmpty())
        assertEquals(1, chunks.first().pageNumber)
        assertTrue(chunks.first().text.contains("Kinematics") || chunks.first().text.contains("Dynamics"))
    }

    @Test
    fun `empty document produces empty chunks and never generates fake content`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val extractor = PdfTextExtractor(context)

        val emptyInputStream = ByteArrayInputStream("   \n\t  ".toByteArray(Charsets.UTF_8))
        val chunks = extractor.extractFromPlainText(emptyInputStream)

        assertTrue("Empty document must return empty chunks list", chunks.isEmpty())
    }

    @Test
    fun `corrupted or unreadable PDF produces diagnostic notice without fake physics chunks`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val extractor = PdfTextExtractor(context)

        val corruptedPdfBytes = "%PDF-1.4\ncorrupted junk binary data that cannot flate decode\n%%EOF".toByteArray(Charsets.ISO_8859_1)
        val chunks = extractor.extractFromPdf(ByteArrayInputStream(corruptedPdfBytes), "Calculus_Syllabus.pdf")

        assertTrue("Should return a single diagnostic chunk", chunks.isNotEmpty())
        val message = chunks.first().text
        assertTrue("Must state failure to extract text", message.contains("Could not extract readable text"))
        org.junit.Assert.assertFalse("Must never inject fake physics content", message.contains("Newton") || message.contains("friction"))
    }

    @Test
    fun `extractFromPlainText handles arbitrary subjects accurately without physics bias`() {
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
        org.junit.Assert.assertFalse("Must not inject physics keywords", chunks.any { it.text.contains("kinematics") || it.text.contains("gravity") })
    }
}
