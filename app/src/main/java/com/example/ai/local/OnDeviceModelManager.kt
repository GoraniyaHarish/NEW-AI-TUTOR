package com.example.ai.local

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class ModelDownloadState {
    object NotDownloaded : ModelDownloadState()
    data class Downloading(val progressPercent: Int, val downloadedMb: Int, val totalMb: Int) : ModelDownloadState()
    object Downloaded : ModelDownloadState()
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
        description = "Optimized on-device LLM for open-ended pedagogical reasoning, physics proofs, and deep offline tutoring.",
        targetFileName = "gemma-2b-it-cpu-int4.bin"
    )

    private val _downloadState = MutableStateFlow<ModelDownloadState>(checkInitialState())
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    private val _isNeuralEngineEnabled = MutableStateFlow(false)
    val isNeuralEngineEnabled: StateFlow<Boolean> = _isNeuralEngineEnabled.asStateFlow()

    private fun getModelFile(): File {
        val dir = context.getExternalFilesDir("models") ?: context.filesDir
        return File(dir, availableModel.targetFileName)
    }

    private fun checkInitialState(): ModelDownloadState {
        val file = getModelFile()
        return if (file.exists() && file.length() > 0) {
            ModelDownloadState.Downloaded
        } else {
            ModelDownloadState.NotDownloaded
        }
    }

    fun startDownload(onCompleted: () -> Unit = {}) {
        if (_downloadState.value is ModelDownloadState.Downloading) return
        _downloadState.value = ModelDownloadState.Downloading(
            progressPercent = 0,
            downloadedMb = 0,
            totalMb = availableModel.sizeMb
        )

        downloadJob = scope.launch {
            val totalMb = availableModel.sizeMb
            var currentMb = 0

            // Progressive download simulation for demo / manageable downloading
            while (currentMb < totalMb) {
                currentMb += (totalMb / 25)
                if (currentMb > totalMb) currentMb = totalMb
                val progress = ((currentMb.toFloat() / totalMb.toFloat()) * 100).toInt()
                _downloadState.value = ModelDownloadState.Downloading(
                    progressPercent = progress,
                    downloadedMb = currentMb,
                    totalMb = totalMb
                )
                delay(300)
            }

            // Create model placeholder file to record downloaded state
            val file = getModelFile()
            file.parentFile?.mkdirs()
            file.writeText("LEARNMATE_ON_DEVICE_GEMMA_WEIGHTS_INDEX_V1")

            _downloadState.value = ModelDownloadState.Downloaded
            _isNeuralEngineEnabled.value = true
            onCompleted()
        }
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

    fun setNeuralEngineEnabled(enabled: Boolean) {
        if (_downloadState.value is ModelDownloadState.Downloaded) {
            _isNeuralEngineEnabled.value = enabled
        }
    }

    fun isReadyForInference(): Boolean {
        return _downloadState.value is ModelDownloadState.Downloaded && _isNeuralEngineEnabled.value
    }
}
