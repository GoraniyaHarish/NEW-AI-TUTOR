package com.example.ai.local

/**
 * Interface representing an optional on-device neural model engine
 * (e.g., Google Gemma, LiteRT, MediaPipe GenAI).
 */
interface LocalModelEngine {
    val isModelAvailable: Boolean
    val modelName: String?

    suspend fun generate(prompt: String, context: String): String?
}

/**
 * Default implementation when no local neural model weights are installed on the device.
 * Reports honest status to prevent faking on-device AI.
 */
class UninstalledLocalModelEngine : LocalModelEngine {
    override val isModelAvailable: Boolean = false
    override val modelName: String? = null

    override suspend fun generate(prompt: String, context: String): String? {
        return null
    }
}
