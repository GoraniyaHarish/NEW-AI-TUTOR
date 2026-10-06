package com.example.learning

import com.example.data.local.entity.SkillEntity
import com.example.learning.revision.CheatSheetGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheatSheetGeneratorTest {

    @Test
    fun `generateCheatSheet for Newton skill contains core laws and pitfalls`() {
        val skill = SkillEntity(
            id = 10,
            courseId = 1,
            name = "Newton's Second Law",
            description = "F=ma",
            chapter = "Dynamics",
            sourceDocumentName = "Physics Notes.pdf",
            sourcePage = 24
        )

        val sheet = CheatSheetGenerator.generateCheatSheet(skill)

        assertNotNull(sheet)
        assertEquals("Physics Notes.pdf", sheet.sourceDoc)
        assertEquals(24, sheet.sourcePage)
        assertTrue(sheet.keyFormulas.any { it.first.contains("F_net") || it.first.contains("F =") })
        assertTrue(sheet.coreDefinitions.isNotEmpty())
        assertTrue(sheet.examPitfalls.isNotEmpty())
    }

    @Test
    fun `generateCheatSheet for Friction skill contains static and kinetic formulas`() {
        val skill = SkillEntity(
            id = 11,
            courseId = 1,
            name = "Friction Analysis",
            description = "Static and kinetic",
            chapter = "Dynamics",
            sourceDocumentName = "Physics Notes.pdf",
            sourcePage = 28
        )

        val sheet = CheatSheetGenerator.generateCheatSheet(skill)

        assertNotNull(sheet)
        assertTrue(sheet.keyFormulas.any { it.first.contains("f_s") })
        assertTrue(sheet.keyFormulas.any { it.first.contains("f_k") })
    }
}
