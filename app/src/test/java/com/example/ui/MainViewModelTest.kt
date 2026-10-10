package com.example.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.ai.local.ModelDownloadState
import com.example.core.util.AppThemeMode
import com.example.core.storage.ExtractedChunk
import com.example.data.local.entity.SkillEntity
import com.example.data.local.entity.QuestionEntity
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.SelectedFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
@Config(sdk = [35])
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
        val db = com.example.data.local.database.LearnMateDatabase.getInstance(application)
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            db.clearAllTables()
        }
        viewModel = MainViewModel(application)
        viewModel.modelManager.deleteModel()
    }

    @After
    fun tearDown() {
        if (::viewModel.isInitialized) {
            viewModel.modelManager.deleteModel()
        }
        val db = com.example.data.local.database.LearnMateDatabase.getInstance(application)
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            db.clearAllTables()
        }
        Dispatchers.resetMain()
    }

    @Test
    fun `fresh installation starts without seeded sample data`() = runTest(testDispatcher) {
        viewModel.repository.ensureInitialData()
        val activeCourseId = viewModel.activeCourseId.value
        assertEquals(null, activeCourseId)
        assertTrue(viewModel.allCourses.value.isEmpty())
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
    fun `theme choice is restored after view model recreation`() = runTest(testDispatcher) {
        viewModel.setThemeMode(AppThemeMode.DARK)
        val recreated = MainViewModel(application)
        assertEquals(AppThemeMode.DARK, recreated.themeMode.value)
        viewModel.setThemeMode(AppThemeMode.SYSTEM)
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
    fun `course creation reports empty imported material without creating a course`() = runTest(testDispatcher) {
        advanceUntilIdle()
        var failure: String? = null
        var created = false

        viewModel.createCourseWithFiles(
            title = "Empty course",
            description = "",
            files = listOf(SelectedFileItem("empty.txt", "TXT", "0 KB", customText = "   ")),
            onFailure = { failure = it },
            onCreated = { created = true }
        )
        advanceUntilIdle()

        assertFalse(created)
        assertTrue(failure?.contains("empty", ignoreCase = true) == true)
        assertTrue(viewModel.repository.getAllCourses().first().isEmpty())
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
        
        // Advance testDispatcher and yield real time for IO pool
        for (i in 0 until 30) {
            advanceUntilIdle()
            if (createdCourseId != 0L) break
            Thread.sleep(50)
        }

        assertTrue("Course ID must be generated > 0", createdCourseId > 0)
        viewModel.selectCourse(createdCourseId)

        // Launch collectors for StateFlows
        val docJob = launch { viewModel.documents.collect {} }
        val skillJob = launch { viewModel.skills.collect {} }
        
        for (i in 0 until 30) {
            advanceUntilIdle()
            if (viewModel.documents.value.isNotEmpty()) break
            Thread.sleep(50)
        }

        val course = viewModel.repository.getCourse(createdCourseId)
        assertNotNull(course)
        assertEquals("Biology 101", course?.title)
        assertFalse(course?.isDemo ?: true)

        val docs = viewModel.documents.value
        assertEquals(1, docs.size)
        assertEquals("Cell_Biology_Notes.txt", docs.first().fileName)

        // Process materials and verify dynamic skills
        val processed = viewModel.repository.processMaterialAndBuildSkillMap(createdCourseId)
        assertTrue("Processing material must return true for valid document", processed)

        for (i in 0 until 30) {
            advanceUntilIdle()
            if (viewModel.skills.value.isNotEmpty()) break
            Thread.sleep(50)
        }

        val skills = viewModel.skills.value
        assertTrue("Skills must be extracted from biology document", skills.isNotEmpty())
        assertTrue("Discovered skill must reflect biology document content", skills.any { it.name.contains("Cellular Biology", ignoreCase = true) })
        assertFalse("Must not inject hardcoded physics topics", skills.any { it.name.contains("Kinematics", ignoreCase = true) || it.name.contains("Newton", ignoreCase = true) })

        docJob.cancel()
        skillJob.cancel()
    }


    @Test
    fun `removing a course deletes its source documents and clears active selection`() {
        val database = com.example.data.local.database.LearnMateDatabase.getInstance(application)
        val courseId = kotlinx.coroutines.runBlocking {
            viewModel.repository.createCourse("Course to remove", "Test-only course")
        }
        kotlinx.coroutines.runBlocking {
            viewModel.repository.addDocument(
                courseId = courseId,
                fileName = "notes.txt",
                fileType = "TXT",
                fileSize = "1 KB",
                extractedChunks = listOf(ExtractedChunk(1, 0, "Chapter 1: Real Notes\\nThis is real imported course content."))
            )
            viewModel.repository.deleteCourse(courseId)
        }

        assertEquals(null, viewModel.repository.getCourse(courseId))
        assertTrue(viewModel.repository.getAllCourses().first().isEmpty())
        assertTrue(database.documentDao().getDocumentsSync(courseId).isEmpty())
        assertTrue(database.documentChunkDao().getChunksSync(courseId).isEmpty())
        assertEquals(null, viewModel.activeCourseId.value)
    }

    @Test
    fun `removing a document clears stale generated learning data when no documents remain`() {
        val database = com.example.data.local.database.LearnMateDatabase.getInstance(application)
        val courseId = kotlinx.coroutines.runBlocking {
            viewModel.repository.createCourse("Document removal", "Test-only course")
        }
        val documentId = kotlinx.coroutines.runBlocking {
            viewModel.repository.addDocument(
                courseId = courseId,
                fileName = "notes.txt",
                fileType = "TXT",
                fileSize = "1 KB",
                extractedChunks = listOf(ExtractedChunk(1, 0, "Chapter 1: Real Notes\\nThis is real imported course content."))
            )
        }
        kotlinx.coroutines.runBlocking {
            val skillId = database.skillDao().insertSkill(
                SkillEntity(courseId = courseId, name = "Real Notes", description = "From source", chapter = "Course Topics")
            )
            database.questionDao().insertQuestions(
                listOf(
                    QuestionEntity(
                        courseId = courseId,
                        skillId = skillId,
                        questionText = "Question from source",
                        optionA = "A", optionB = "B", optionC = "C", optionD = "D",
                        correctAnswerIndex = 0,
                        explanation = "From source"
                    )
                )
            )
            assertTrue(viewModel.repository.deleteDocument(courseId, documentId))
        }

        assertTrue(database.documentDao().getDocumentsSync(courseId).isEmpty())
        assertTrue(database.documentChunkDao().getChunksSync(courseId).isEmpty())
        assertTrue(database.skillDao().getSkillsSync(courseId).isEmpty())
        assertTrue(database.questionDao().getQuestionsSync(courseId).isEmpty())
    }

}
