package com.example.ai

import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.DocumentChunkEntity
import com.example.data.local.entity.QuestionEntity
import com.example.data.local.entity.SkillEntity

data class TutorResponse(
    val answer: String,
    val sourceDocName: String? = null,
    val sourcePage: Int? = null,
    val isOffline: Boolean = true,
    val confidence: Float = 0.95f,
    val isGroundedInMaterial: Boolean = sourceDocName != null,
    val modelUsed: String? = null
)

data class LessonExplanation(
    val title: String,
    val summary: String,
    val keyPoints: List<String>,
    val examples: List<String>,
    val sourceDocName: String,
    val sourcePage: Int
)

interface AIService {
    suspend fun answerTutor(
        query: String,
        skill: SkillEntity?,
        relevantChunks: List<DocumentChunkEntity>,
        courseId: Long
    ): TutorResponse

    suspend fun answerTutor(
        query: String,
        skill: SkillEntity?,
        relevantChunks: List<DocumentChunkEntity>,
        courseId: Long,
        conversationHistory: List<ChatMessageEntity>
    ): TutorResponse = answerTutor(query, skill, relevantChunks, courseId)

    suspend fun generateExplanation(
        skill: SkillEntity,
        relevantChunks: List<DocumentChunkEntity>
    ): LessonExplanation

    suspend fun generateQuestionsForSkill(
        skill: SkillEntity,
        count: Int,
        difficulty: String
    ): List<QuestionEntity>
}
