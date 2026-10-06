package com.example.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.local.ModelDownloadState
import com.example.ai.local.OnDeviceModelManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
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
}
