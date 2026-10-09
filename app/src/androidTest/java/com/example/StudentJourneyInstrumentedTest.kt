package com.example

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end student journeys executed on an Android emulator.
 * These tests exercise real Compose UI interactions rather than only testing view-model methods.
 */
@RunWith(AndroidJUnit4::class)
class StudentJourneyInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun ensureHome() {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithTag("onboarding_screen").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("home_screen").fetchSemanticsNodes().isNotEmpty()
        }

        if (composeRule.onAllNodesWithTag("onboarding_screen").fetchSemanticsNodes().isNotEmpty()) {
            // Finish the intro; onboarding leads to Home so students can choose their next step.
            while (composeRule.onAllNodesWithTag("get_started_button").fetchSemanticsNodes().isEmpty()) {
                composeRule.onNodeWithTag("onboarding_next_button").performClick()
            }
            composeRule.onNodeWithTag("get_started_button").performClick()
        }

        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithTag("home_screen").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(composeRule.onAllNodesWithTag("home_screen").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun studentCanOpenTutorReceiveAReplyAndChangeOfflineSetting() {
        ensureHome()

        // A real question requires a real course and notes; do not silently test an empty tutor.
        composeRule.onNodeWithTag("nav_item_courses").performClick()
        composeRule.onNodeWithTag("fab_create_course").performClick()
        composeRule.onNodeWithTag("course_name_input").performTextInput("Tutor Test Biology")
        composeRule.onNodeWithTag("course_description_input")
            .performTextInput("Course used to verify the tutoring journey.")
        composeRule.onNodeWithTag("paste_notes_button").performClick()
        composeRule.onNodeWithTag("custom_notes_title_input").performTextInput("Photosynthesis")
        composeRule.onNodeWithTag("custom_notes_content_input").performTextInput(
            "Photosynthesis uses light energy to convert carbon dioxide and water into glucose and oxygen. " +
                "Chlorophyll absorbs light in chloroplasts."
        )
        composeRule.onNodeWithText("Add Notes").performClick()
        composeRule.onNodeWithTag("build_my_course_submit_button").performClick()
        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodesWithTag("skill_map_screen").fetchSemanticsNodes().isNotEmpty()
        }

        // Open tutor, send a question grounded in the newly created notes, and require an assistant reply.
        composeRule.onNodeWithTag("nav_item_tutor").performClick()
        assertTrue(composeRule.onAllNodesWithTag("ai_tutor_screen").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithTag("tutor_message_input")
            .performTextInput("What does photosynthesis use to make glucose?")
        composeRule.onNodeWithTag("send_tutor_message_button").performClick()

        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithTag("tutor_assistant_message").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "The tutor must render an assistant reply, not just echo the student's question",
            composeRule.onAllNodesWithTag("tutor_assistant_message").fetchSemanticsNodes().isNotEmpty()
        )

        // Visit profile/settings and toggle offline simulation using the actual UI.
        composeRule.onNodeWithTag("nav_item_profile").performClick()
        composeRule.onNodeWithTag("profile_settings_tile").performClick()
        assertTrue(composeRule.onAllNodesWithTag("settings_screen").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithTag("offline_simulation_switch").performClick()
        assertTrue(composeRule.onAllNodesWithTag("settings_screen").fetchSemanticsNodes().isNotEmpty())

        // Return to tutor and verify the screen remains navigable after changing the setting.
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag("nav_item_tutor").performClick()
        assertTrue(composeRule.onAllNodesWithTag("ai_tutor_screen").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun studentCanCreateCourseAndAddTheirOwnStudyNotes() {
        ensureHome()

        composeRule.onNodeWithTag("nav_item_courses").performClick()
        assertTrue(composeRule.onAllNodesWithTag("course_list_screen").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithTag("fab_create_course").performClick()
        assertTrue(composeRule.onAllNodesWithTag("build_my_course_screen").fetchSemanticsNodes().isNotEmpty())

        composeRule.onNodeWithTag("course_name_input").performTextInput("Student Biology")
        composeRule.onNodeWithTag("course_description_input")
            .performTextInput("A practice course created during the student journey test.")

        composeRule.onNodeWithTag("paste_notes_button").performClick()
        composeRule.onNodeWithTag("custom_notes_title_input")
            .performTextInput("Photosynthesis Notes")
        composeRule.onNodeWithTag("custom_notes_content_input")
            .performTextInput(
                "Photosynthesis uses light energy to convert carbon dioxide and water into glucose and oxygen. " +
                    "Chlorophyll absorbs light in chloroplasts."
            )
        composeRule.onNodeWithText("Add Notes").performClick()
        composeRule.onNodeWithTag("build_my_course_submit_button").performClick()

        // Course processing should complete and open the learning map for the new course.
        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodesWithTag("skill_map_screen").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(composeRule.onAllNodesWithTag("skill_map_screen").fetchSemanticsNodes().isNotEmpty())
    }
}
