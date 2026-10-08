package com.example.ai

import com.example.ai.cloud.CloudAIService
import com.example.ai.local.LocalAIService
import com.example.core.network.NetworkMonitor
import com.example.data.local.entity.ChatMessageEntity
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
    ): TutorResponse = answerTutor(query, skill, relevantChunks, courseId, emptyList())

    override suspend fun answerTutor(
        query: String,
        skill: SkillEntity?,
        relevantChunks: List<DocumentChunkEntity>,
        courseId: Long,
        conversationHistory: List<ChatMessageEntity>
    ): TutorResponse {
        val isOnline = networkMonitor.isOnline.value
        val hasCloudKey = cloudAI.isConfigured()

        if (isOnline && hasCloudKey) {
            try {
                return cloudAI.answerTutor(query, skill, relevantChunks, courseId, conversationHistory)
            } catch (_: Exception) {
                // Cloud attempt failed or timed out: fall back seamlessly to honest local tutor
            }
        }

        // Offline or fallback path:
        return localAI.answerTutor(query, skill, relevantChunks, courseId, conversationHistory)
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
                // Fall back to local
            }
        }

        return localAI.generateExplanation(skill, relevantChunks)
    }

    override suspend fun generateQuestionsForSkill(
        skill: SkillEntity,
        count: Int,
        difficulty: String,
        relevantChunks: List<DocumentChunkEntity>
    ): List<QuestionEntity> {
        val isOnline = networkMonitor.isOnline.value
        val hasCloudKey = cloudAI.isConfigured()

        if (isOnline && hasCloudKey) {
            try {
                val cloudQuestions = cloudAI.generateQuestionsForSkill(skill, count, difficulty, relevantChunks)
                if (cloudQuestions.isNotEmpty()) {
                    return cloudQuestions
                }
            } catch (_: Exception) {
                // Fall back to local
            }
        }

        return localAI.generateQuestionsForSkill(skill, count, difficulty, relevantChunks)
    }
}
