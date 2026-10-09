package com.example.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.local.DynamicLocalModelEngine
import com.example.ai.local.ModelDownloadState
import com.example.ai.local.OnDeviceModelManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnDeviceModelManagerTest {

    @Test
    fun `initial state is NotDownloaded`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = OnDeviceModelManager(context)

        assertNotNull(manager.availableModel)
        assertEquals("gemma_2b_it_int4", manager.availableModel.id)
        assertFalse(manager.isReadyForInference())
    }

    @Test
    fun `cancel download reverts state to NotDownloaded`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = OnDeviceModelManager(context)

        manager.startDownload()
        manager.cancelDownload()

        assertEquals(ModelDownloadState.NotDownloaded, manager.downloadState.value)
        assertFalse(manager.isReadyForInference())
    }
    @Test
    fun `download request is honest and never claims a model was installed`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = OnDeviceModelManager(context)

        manager.startDownload()

        val state = manager.downloadState.value
        assertTrue(state is ModelDownloadState.Error)
        assertTrue((state as ModelDownloadState.Error).message.contains("not implemented"))
        assertFalse(manager.isReadyForInference())
        assertFalse(manager.isNeuralEngineEnabled.value)
    }

    @Test
    fun `dynamic engine never fakes neural inference when runtime is unavailable`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = DynamicLocalModelEngine(OnDeviceModelManager(context))

        assertFalse(engine.isModelAvailable)
        assertNull(engine.modelName)
        assertNull(engine.generate("Explain photosynthesis", "Photosynthesis notes"))
    }

}
