package com.example.ai

import android.util.Log
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
            } catch (e: Exception) {
                Log.e(TAG, "Cloud tutor request failed; using local tutor fallback", e)
                val localResponse = localAI.answerTutor(query, skill, relevantChunks, courseId, conversationHistory)
                return localResponse.copy(
                    answer = "Online AI could not respond, so this answer was generated offline. " +
                        "Check your connection and Gemini API key, then try again.\n\n" +
                        localResponse.answer
                )
            }
        }

        val localResponse = localAI.answerTutor(query, skill, relevantChunks, courseId, conversationHistory)
        return if (isOnline && !hasCloudKey) {
            Log.w(TAG, "Internet is available, but a Gemini API key is not configured for this build")
            localResponse.copy(
                answer = "Online Gemini AI is not configured in this installation, so this answer was generated offline.\n\n" +
                    localResponse.answer
            )
        } else {
            localResponse
        }
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
            } catch (e: Exception) {
                Log.e(TAG, "Cloud lesson explanation failed; using local fallback", e)
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
                Log.w(TAG, "Cloud quiz generation returned no valid questions; using local fallback")
            } catch (e: Exception) {
                Log.e(TAG, "Cloud quiz generation failed; using local fallback", e)
            }
        }

        return localAI.generateQuestionsForSkill(skill, count, difficulty, relevantChunks)
    }

    private companion object {
        const val TAG = "LearnMateAIRouter"
    }
}
