package com.example

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
            composeRule.onAllNodesWithTag("skip_to_demo_button").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("home_screen").fetchSemanticsNodes().isNotEmpty()
        }

        if (composeRule.onAllNodesWithTag("skip_to_demo_button").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("skip_to_demo_button").performClick()
        }

        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithTag("home_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("home_screen").assertExists()
    }

    @Test
    fun studentCanOpenTutorAskAQuestionAndChangeOfflineSetting() {
        ensureHome()

        // Open tutor and send a real question through the text field.
        composeRule.onNodeWithTag("nav_item_tutor").performClick()
        composeRule.onNodeWithTag("ai_tutor_screen").assertExists()
        composeRule.onNodeWithTag("tutor_message_input")
            .performTextInput("Explain the main concepts in my notes.")
        composeRule.onNodeWithTag("send_tutor_message_button").performClick()

        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithTag("ai_tutor_screen").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithTag("tutor_message_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Explain the main concepts in my notes.").assertExists()

        // Visit profile/settings and toggle offline simulation using the actual UI.
        composeRule.onNodeWithTag("nav_item_profile").performClick()
        composeRule.onNodeWithTag("profile_settings_tile").performClick()
        composeRule.onNodeWithTag("settings_screen").assertExists()
        composeRule.onNodeWithTag("offline_simulation_switch").performClick()
        composeRule.onNodeWithTag("settings_screen").assertExists()

        // Return to tutor and verify the screen remains navigable after changing the setting.
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag("nav_item_tutor").performClick()
        composeRule.onNodeWithTag("ai_tutor_screen").assertExists()
    }

    @Test
    fun studentCanCreateCourseAndAddTheirOwnStudyNotes() {
        ensureHome()

        composeRule.onNodeWithTag("nav_item_courses").performClick()
        composeRule.onNodeWithTag("course_list_screen").assertExists()
        composeRule.onNodeWithTag("fab_create_course").performClick()
        composeRule.onNodeWithTag("build_my_course_screen").assertExists()

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
        composeRule.onNodeWithTag("skill_map_screen").assertExists()
    }
}
