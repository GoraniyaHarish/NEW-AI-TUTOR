package com.example.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.ai.local.ModelDownloadState
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.SelectedFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
        val db = com.example.data.local.database.LearnMateDatabase.getInstance(application)
        db.clearAllTables()
        viewModel = MainViewModel(application)
        viewModel.modelManager.deleteModel()
    }

    @After
    fun tearDown() {
        viewModel.modelManager.deleteModel()
        val db = com.example.data.local.database.LearnMateDatabase.getInstance(application)
        db.clearAllTables()
        Dispatchers.resetMain()
    }

    @Test
    fun `fresh installation starts without demo course and demo loading is explicit`() = runTest(testDispatcher) {
        viewModel.repository.ensureInitialData()
        val activeCourseId = viewModel.activeCourseId.value
        // Fresh production install must start empty
        org.junit.Assert.assertNull("Fresh install must not automatically seed demo course", activeCourseId)

        // Explicit demo load must work on demand
        val loadedDemoId = viewModel.repository.resetToDemoCourse()
        viewModel.selectCourse(loadedDemoId)
        val course = viewModel.repository.getCourse(loadedDemoId)
        assertNotNull(course)
        assertEquals("Physics", course?.title)
        assertTrue(course?.isDemo == true)
    }

    @Test
    fun `toggle offline simulation toggles state`() = runTest(testDispatcher) {
        advanceUntilIdle()
        val initialOffline = viewModel.isSimulatedOffline.value

        viewModel.toggleOfflineSimulation()
        assertEquals(!initialOffline, viewModel.isSimulatedOffline.value)

        viewModel.toggleOfflineSimulation()
        assertEquals(initialOffline, viewModel.isSimulatedOffline.value)
    }

    @Test
    fun `neural model manager download control`() = runTest(testDispatcher) {
        viewModel.modelManager.deleteModel()
        advanceUntilIdle()
        assertEquals(ModelDownloadState.NotDownloaded, viewModel.modelDownloadState.value)

        viewModel.modelManager.startDownload()
        assertTrue(
            viewModel.modelDownloadState.value is ModelDownloadState.Error ||
            viewModel.modelDownloadState.value is ModelDownloadState.Downloading
        )

        viewModel.modelManager.cancelDownload()
        assertEquals(ModelDownloadState.NotDownloaded, viewModel.modelDownloadState.value)
    }

    @Test
    fun `diagnostic question answering updates state`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.answerDiagnosticQuestion(101L, 2)
        assertEquals(2, viewModel.diagnosticAnswers.value[101L])

        viewModel.useDiagnosticHint(101L)
        assertTrue(viewModel.diagnosticHintsUsed.value[101L] == true)
    }

    @Test
    fun `selectCourse changes activeCourseId`() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.selectCourse(999L)
        assertEquals(999L, viewModel.activeCourseId.value)
    }

    @Test
    fun `createCourseWithFiles ingests custom material and processes real documents without fake fallbacks`() = runTest(testDispatcher) {
        advanceUntilIdle()
        var createdCourseId = 0L

        val customText = """
            Chapter 1: Principles of Cellular Biology
            
            Cells are the basic unit of life in all known living organisms.
            Every eukaryotic cell contains a membrane-bound nucleus and organelles such as mitochondria.
        """.trimIndent()

        val files = listOf(
            SelectedFileItem(
                name = "Cell_Biology_Notes.txt",
                type = "TXT",
                size = "1.2 KB",
                customText = customText
            )
        )

        viewModel.createCourseWithFiles("Biology 101", "Cellular fundamentals", files) { id ->
            createdCourseId = id
        }
        advanceUntilIdle()

        assertTrue(createdCourseId > 0)
        viewModel.selectCourse(createdCourseId)
        advanceUntilIdle()

        val course = viewModel.repository.getCourse(createdCourseId)
        assertNotNull(course)
        assertEquals("Biology 101", course?.title)
        assertFalse(course?.isDemo ?: true)

        val docs = viewModel.documents.value
        assertEquals(1, docs.size)
        assertEquals("Cell_Biology_Notes.txt", docs.first().fileName)

        // Process materials and verify dynamic skills
        viewModel.repository.processMaterialAndBuildSkillMap(createdCourseId)
        advanceUntilIdle()

        val skills = viewModel.skills.value
        assertTrue("Skills must be extracted from biology document", skills.isNotEmpty())
        assertTrue("Discovered skill must reflect biology document content", skills.any { it.name.contains("Cellular Biology", ignoreCase = true) })
        assertFalse("Must not inject hardcoded physics topics", skills.any { it.name.contains("Kinematics", ignoreCase = true) || it.name.contains("Newton", ignoreCase = true) })
    }
}
