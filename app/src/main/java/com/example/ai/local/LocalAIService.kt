package com.example.ai.local

import com.example.ai.AIService
import com.example.ai.LessonExplanation
import com.example.ai.TutorResponse
import com.example.ai.nlp.QueryUnderstanding
import com.example.ai.nlp.TutorIntent
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.DocumentChunkEntity
import com.example.data.local.entity.QuestionEntity
import com.example.data.local.entity.SkillEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalAIService(
    private val localModelEngine: LocalModelEngine = UninstalledLocalModelEngine()
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
    ): TutorResponse = withContext(Dispatchers.Default) {
        val lastAssistantMsg = conversationHistory.lastOrNull { it.role != "user" }?.content
        val analyzed = QueryUnderstanding.analyze(
            query = query,
            currentSkillName = skill?.name,
            lastAssistantMessage = lastAssistantMsg
        )

        val topChunk = relevantChunks.firstOrNull()
        val hasEvidence = topChunk != null && topChunk.text.isNotBlank()

        // 1. If an actual neural on-device model is installed and ready, execute inference
        if (localModelEngine.isModelAvailable) {
            val contextText = relevantChunks.joinToString("\n\n") { it.text }
            val prompt = "Student question: $query\nIntent: ${analyzed.intent}\nContext: $contextText"
            val neuralResponse = localModelEngine.generate(prompt, contextText)
            if (neuralResponse != null) {
                return@withContext TutorResponse(
                    answer = neuralResponse,
                    sourceDocName = if (hasEvidence) topChunk?.sourceDocumentName else null,
                    sourcePage = if (hasEvidence) topChunk?.pageNumber else null,
                    isOffline = true,
                    confidence = 0.95f,
                    isGroundedInMaterial = hasEvidence,
                    modelUsed = localModelEngine.modelName
                )
            }
        }

        // 2. Truthful Local Tutor using student's indexed material
        if (!hasEvidence && (skill == null || skill.description.isBlank())) {
            val missingAnswer = buildString {
                append("This topic wasn't found in your uploaded study materials.\n\n")
                append("Because you are offline and an on-device neural model is not installed, I can only explain concepts directly present in your saved course notes.\n\n")
                append("💡 **Tip:** When you connect to the internet, Cloud AI can answer general questions outside your notes. Or you can ask about any topic from your imported material.")
            }
            return@withContext TutorResponse(
                answer = missingAnswer,
                sourceDocName = null,
                sourcePage = null,
                isOffline = true,
                confidence = 0.5f,
                isGroundedInMaterial = false,
                modelUsed = "offline-knowledge-base"
            )
        }

        // 3. Grounded explanation constructed directly from student's material
        val topicTitle = skill?.name ?: "Topic from ${topChunk?.sourceDocumentName ?: "your notes"}"
        val materialText = topChunk?.text ?: skill?.description ?: ""

        val responseText = buildString {
            when (analyzed.intent) {
                TutorIntent.HINT -> {
                    append("💡 **Tutor Hint for $topicTitle:**\n\n")
                    append("Here is the key relationship from your notes to help you solve this:\n")
                    append("> \"${materialText.take(280).trim()}...\"\n\n")
                    append("**Next Steps:**\n")
                    append("1. Identify what variables or definitions are given.\n")
                    append("2. Apply the core rule highlighted above.\n")
                    append("👉 Give it a shot, or tell me which part you're stuck on!")
                }

                TutorIntent.SIMPLIFY -> {
                    append("Let's break down **$topicTitle** into simple terms from your notes:\n\n")
                    val sentences = materialText.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
                    if (sentences.isNotEmpty()) {
                        sentences.take(4).forEachIndexed { idx, s ->
                            append("• **Key Idea ${idx + 1}:** ${s.trim()}\n")
                        }
                    } else {
                        append("• ${materialText.trim()}\n")
                    }
                    append("\nDoes this clearer breakdown make sense? Tell me which point to expand on!")
                }

                TutorIntent.EXAMPLE -> {
                    append("### 📝 Example & Application: $topicTitle\n\n")
                    append("From your study notes:\n")
                    append("> ${materialText.take(350).trim()}\n\n")
                    append("**Walkthrough:**\n")
                    append("When applying this concept, identify how the input parameters match the definition in your text, and verify the resulting units.\n\n")
                    append("Would you like a practice problem to test your understanding?")
                }

                TutorIntent.SUMMARY, TutorIntent.REVISE -> {
                    append("### 📋 Quick Recap: $topicTitle\n\n")
                    val sentences = materialText.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
                    sentences.take(3).forEach { s ->
                        append("✔ ${s.trim()}\n")
                    }
                    append("\n*(Extracted from ${topChunk?.sourceDocumentName ?: skill?.sourceDocumentName ?: "Course Notes"}, Page ${topChunk?.pageNumber ?: skill?.sourcePage ?: 1})*")
                }

                TutorIntent.QUIZ -> {
                    append("### 🧠 Quick Check: $topicTitle\n\n")
                    append("Based on your uploaded material:\n")
                    append("> \"${materialText.take(200).trim()}...\"\n\n")
                    append("**Question for you:** How would you state the primary condition or definition described here in your own words?")
                }

                else -> {
                    append("### 📚 $topicTitle\n\n")
                    append("According to your uploaded notes:\n\n")
                    append(materialText.trim())
                    append("\n\n")
                    append("💡 **Check for Understanding:** Would you like a simplified breakdown, an example, or a practice quiz on this?")
                }
            }

            append("\n\n*(Grounded in your offline notes from ${topChunk?.sourceDocumentName ?: skill?.sourceDocumentName}. On-device neural model not installed; showing structured offline notes analysis.)*")
        }

        TutorResponse(
            answer = responseText,
            sourceDocName = topChunk?.sourceDocumentName ?: skill?.sourceDocumentName,
            sourcePage = topChunk?.pageNumber ?: skill?.sourcePage,
            isOffline = true,
            confidence = 0.92f,
            isGroundedInMaterial = true,
            modelUsed = "offline-knowledge-base"
        )
    }

    override suspend fun generateExplanation(
        skill: SkillEntity,
        relevantChunks: List<DocumentChunkEntity>
    ): LessonExplanation = withContext(Dispatchers.Default) {
        val topChunk = relevantChunks.firstOrNull()
        val hasEvidence = relevantChunks.isNotEmpty() && topChunk != null && topChunk.text.isNotBlank()

        if (!hasEvidence || topChunk == null) {
            return@withContext LessonExplanation(
                title = skill.name,
                summary = "No readable course material was available for '${skill.name}'. Please import relevant course notes or documents to generate a grounded explanation.",
                keyPoints = emptyList(),
                examples = emptyList(),
                sourceDocName = null,
                sourcePage = null
            )
        }

        val baseText = relevantChunks.joinToString("\n") { it.text }
        val sentences = baseText.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotBlank() }
        val summary = if (sentences.isNotEmpty()) {
            sentences.take(2).joinToString(" ")
        } else {
            "Grounded explanation for ${skill.name} based on retrieved notes."
        }

        val keyPoints = if (sentences.size >= 3) {
            sentences.drop(2).take(4)
        } else if (sentences.isNotEmpty()) {
            sentences.take(2)
        } else {
            emptyList()
        }

        val examples = listOf(
            "Retrieved passage in ${topChunk.sourceDocumentName} (Page ${topChunk.pageNumber})."
        )

        LessonExplanation(
            title = skill.name,
            summary = summary,
            keyPoints = keyPoints,
            examples = examples,
            sourceDocName = topChunk.sourceDocumentName,
            sourcePage = topChunk.pageNumber
        )
    }

    override suspend fun generateQuestionsForSkill(
        skill: SkillEntity,
        count: Int,
        difficulty: String,
        relevantChunks: List<DocumentChunkEntity>
    ): List<QuestionEntity> = withContext(Dispatchers.Default) {
        val topChunk = relevantChunks.firstOrNull { it.text.isNotBlank() }
        if (topChunk == null || topChunk.text.isBlank()) {
            return@withContext emptyList()
        }

        val snippet = topChunk.text.split(Regex("(?<=[.!?])\\s+")).firstOrNull()?.trim() ?: topChunk.text.take(120)
        
        // Vary correct answer index deterministically from 0 to 3 using java.util.Random with a deterministic seed
        val seed = skill.id * 31L + count * 17L + topChunk.id * 7L + difficulty.hashCode()
        val correctIndex = java.util.Random(seed).nextInt(4)

        val optionsList = mutableListOf(
            "An unrelated concept from an out-of-scope chapter.",
            "A contradictory statement explicitly disproven in the text.",
            "A hypothetical theorem with no empirical grounding."
        )
        // Deterministically insert snippet at correctIndex
        optionsList.add(correctIndex, snippet)

        val q1 = QuestionEntity(
            courseId = skill.courseId,
            skillId = skill.id,
            questionText = "According to document '${topChunk.sourceDocumentName}' (Page ${topChunk.pageNumber}), which statement accurately reflects '${skill.name}'?",
            optionA = optionsList[0],
            optionB = optionsList[1],
            optionC = optionsList[2],
            optionD = optionsList[3],
            correctAnswerIndex = correctIndex,
            explanation = "Verified directly from page ${topChunk.pageNumber} of ${topChunk.sourceDocumentName}.",
            difficulty = difficulty,
            hint = "Check page ${topChunk.pageNumber} in ${topChunk.sourceDocumentName}.",
            sourceDocumentName = topChunk.sourceDocumentName,
            sourcePage = topChunk.pageNumber
        )

        listOf(q1).take(count)
    }
}
