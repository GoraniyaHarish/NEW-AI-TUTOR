package com.example.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.ai.local.ModelDownloadState
import com.example.ui.viewmodel.MainViewModel
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
        viewModel = MainViewModel(application)
        viewModel.modelManager.deleteModel()
    }

    @After
    fun tearDown() {
        viewModel.modelManager.deleteModel()
        Dispatchers.resetMain()
    }

    @Test
    fun `initial setup initializes courses and active course`() = runTest(testDispatcher) {
        viewModel.repository.ensureInitialData()
        val activeCourseId = viewModel.activeCourseId.value
        assertNotNull(activeCourseId)

        val course = viewModel.repository.getCourse(activeCourseId!!)
        assertNotNull(course)
        assertEquals("Physics", course?.title)
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
        assertTrue(viewModel.modelDownloadState.value is ModelDownloadState.Downloading)

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
}
