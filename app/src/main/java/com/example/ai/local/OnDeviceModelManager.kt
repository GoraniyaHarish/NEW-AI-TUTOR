package com.example.ai.local

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

sealed class ModelDownloadState {
    object NotDownloaded : ModelDownloadState()
    data class Downloading(val progressPercent: Int, val downloadedMb: Int, val totalMb: Int) : ModelDownloadState()
    data class Downloaded(val filePath: String, val sizeMb: Int) : ModelDownloadState()
    data class Error(val message: String) : ModelDownloadState()
}

data class NeuralModelInfo(
    val id: String,
    val name: String,
    val architecture: String,
    val sizeMb: Int,
    val ramRequiredGb: Float,
    val description: String,
    val targetFileName: String
)

class OnDeviceModelManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var downloadJob: Job? = null

    val availableModel = NeuralModelInfo(
        id = "gemma_2b_it_int4",
        name = "Gemma 2B IT (INT4 Quantized)",
        architecture = "Google Gemma 2",
        sizeMb = 1420,
        ramRequiredGb = 2.5f,
        description = "Planned optional model. Download and neural inference are not implemented in this build.",
        targetFileName = "gemma-2b-it-cpu-int4.bin"
    )

    private val _downloadState = MutableStateFlow<ModelDownloadState>(ModelDownloadState.NotDownloaded)
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    private val _isNeuralEngineEnabled = MutableStateFlow(false)
    val isNeuralEngineEnabled: StateFlow<Boolean> = _isNeuralEngineEnabled.asStateFlow()

    fun getModelFile(): File {
        val dir = context.getExternalFilesDir("models") ?: context.filesDir
        return File(dir, availableModel.targetFileName)
    }

    @Suppress("UNUSED_PARAMETER")
    fun startDownload(onCompleted: () -> Unit = {}) {
        // This build intentionally does not claim to download or run model weights.
        _downloadState.value = ModelDownloadState.Error(
            "On-device model download and neural inference are not implemented in this build. No model was downloaded or run. Use the Offline Knowledge Base or Cloud AI."
        )
        _isNeuralEngineEnabled.value = false
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _downloadState.value = ModelDownloadState.NotDownloaded
    }

    fun deleteModel() {
        cancelDownload()
        val file = getModelFile()
        if (file.exists()) {
            file.delete()
        }
        _downloadState.value = ModelDownloadState.NotDownloaded
        _isNeuralEngineEnabled.value = false
    }

    @Suppress("UNUSED_PARAMETER")
    fun setNeuralEngineEnabled(enabled: Boolean) {
        // Keep neural inference disabled until a real, verified runtime is integrated.
        _isNeuralEngineEnabled.value = false
    }

    fun isReadyForInference(): Boolean = false
}
