package com.example.ai

import com.example.ai.cloud.CloudAIService
import com.example.ai.local.LocalAIService
import com.example.core.network.NetworkMonitor
import com.example.data.local.entity.DocumentChunkEntity
import com.example.data.local.entity.QuestionEntity
import com.example.data.local.entity.SkillEntity

class AIRouter(
    private val localAI: LocalAIService,
    private val cloudAI: CloudAIService,
    private val networkMonitor: NetworkMonitor
) : AIService {

    override suspend fun answerTutor(
        query: String,
        skill: SkillEntity?,
        relevantChunks: List<DocumentChunkEntity>,
        courseId: Long
    ): TutorResponse {
        val isOnline = networkMonitor.isOnline.value
        val hasCloudKey = cloudAI.isConfigured()

        if (isOnline && hasCloudKey) {
            try {
                return cloudAI.answerTutor(query, skill, relevantChunks, courseId)
            } catch (_: Exception) {
                // Cloud attempt failed or timed out: fall back seamlessly to local AI
            }
        }

        // Offline or fallback path:
        return localAI.answerTutor(query, skill, relevantChunks, courseId)
    }

    override suspend fun generateExplanation(
        skill: SkillEntity,
        relevantChunks: List<DocumentChunkEntity>
    ): LessonExplanation {
        val isOnline = networkMonitor.isOnline.value
        val hasCloudKey = cloudAI.isConfigured()

        if (isOnline && hasCloudKey) {
            try {
                return cloudAI.generateExplanation(skill, relevantChunks)
            } catch (_: Exception) {
                // Fall back
            }
        }

        return localAI.generateExplanation(skill, relevantChunks)
    }

    override suspend fun generateQuestionsForSkill(
        skill: SkillEntity,
        count: Int,
        difficulty: String
    ): List<QuestionEntity> {
        return localAI.generateQuestionsForSkill(skill, count, difficulty)
    }
}
