package com.example.learning

import com.example.data.local.entity.DocumentChunkEntity
import com.example.data.local.entity.SkillEntity
import com.example.learning.revision.CheatSheetGenerator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheatSheetGeneratorTest {
    private val skill = SkillEntity(
        id = 10,
        courseId = 1,
        name = "Photosynthesis",
        description = "",
        chapter = "Biology",
        sourceDocumentId = 5,
        sourceDocumentName = "Biology Notes.pdf",
        sourcePage = 24
    )

    @Test
    fun `study sheet only contains facts and formulas present in selected source`() {
        val source = DocumentChunkEntity(
            id = 1,
            documentId = 5,
            courseId = 1,
            sourceDocumentName = "Biology Notes.pdf",
            pageNumber = 24,
            text = "Photosynthesis is defined as the process by which plants use light energy. " +
                "The light reaction occurs in chloroplasts. Rate = light intensity times carbon dioxide.",
            chunkIndex = 0
        )

        val sheet = CheatSheetGenerator.generateCheatSheet(skill, listOf(source))

        assertTrue(sheet.hasReadableSource)
        assertTrue(sheet.keyPoints.isNotEmpty())
        assertTrue(sheet.formulas.any { it.contains("Rate = light intensity") })
        assertTrue(sheet.definitions.any { it.contains("Photosynthesis is defined as") })
        assertFalse(sheet.keyPoints.any { it.contains("Newton") })
    }

    @Test
    fun `study sheet for missing material is empty instead of fabricated`() {
        val sheet = CheatSheetGenerator.generateCheatSheet(skill, emptyList())

        assertFalse(sheet.hasReadableSource)
        assertTrue(sheet.keyPoints.isEmpty())
        assertTrue(sheet.formulas.isEmpty())
        assertTrue(sheet.definitions.isEmpty())
        assertTrue(sheet.sourceWarnings.isEmpty())
    }

    @Test
    fun `study sheet never mixes material from a different course`() {
        val otherCourseChunk = DocumentChunkEntity(
            id = 2,
            documentId = 5,
            courseId = 9,
            sourceDocumentName = "Other.pdf",
            pageNumber = 1,
            text = "Newton's second law is F = ma.",
            chunkIndex = 0
        )

        val sheet = CheatSheetGenerator.generateCheatSheet(skill, listOf(otherCourseChunk))

        assertFalse(sheet.hasReadableSource)
        assertTrue(sheet.keyPoints.isEmpty())
        assertTrue(sheet.formulas.isEmpty())
    }
}
