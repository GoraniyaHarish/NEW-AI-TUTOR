package com.example.ui

import com.example.ui.screens.presentTutorText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorTextPresentationTest {
    @Test
    fun `markdown tutor content is converted to readable plain text`() {
        val rendered = presentTutorText(
            "### Main idea\r\n\r\n**Plants** use light.\n- First point\n- Second point\n\n| Term | Meaning |\n| --- | --- |\n| chlorophyll | absorbs light |"
        )

        assertTrue(rendered.contains("Main idea"))
        assertTrue(rendered.contains("Plants use light."))
        assertTrue(rendered.contains("• First point"))
        assertTrue(rendered.contains("Term  •  Meaning"))
        assertTrue(rendered.contains("chlorophyll  •  absorbs light"))
        assertFalse(rendered.contains("**"))
        assertFalse(rendered.contains("| --- |"))
        assertFalse(rendered.contains("\r"))
    }
}
