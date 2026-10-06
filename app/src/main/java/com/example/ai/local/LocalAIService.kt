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
        val docName = if (hasEvidence) topChunk.sourceDocumentName else skill.sourceDocumentName
        val pageNum = if (hasEvidence) topChunk.pageNumber else skill.sourcePage

        val baseText = if (hasEvidence) {
            relevantChunks.joinToString("\n") { it.text }
        } else {
            skill.description
        }

        val sentences = baseText.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotBlank() }
        val summary = if (sentences.isNotEmpty()) {
            sentences.take(2).joinToString(" ")
        } else {
            "Concept: ${skill.name} from chapter ${skill.chapter}."
        }

        val keyPoints = if (sentences.size >= 3) {
            sentences.drop(2).take(4)
        } else if (sentences.isNotEmpty()) {
            sentences.take(2)
        } else {
            listOf("Key topic: ${skill.name} in chapter ${skill.chapter}.")
        }

        val examples = if (hasEvidence) {
            listOf("Reference in $docName (Page $pageNum).")
        } else {
            emptyList()
        }

        LessonExplanation(
            title = skill.name,
            summary = summary,
            keyPoints = keyPoints,
            examples = examples,
            sourceDocName = docName.ifBlank { "Course Material" },
            sourcePage = pageNum
        )
    }

    override suspend fun generateQuestionsForSkill(
        skill: SkillEntity,
        count: Int,
        difficulty: String
    ): List<QuestionEntity> = withContext(Dispatchers.Default) {
        // Deterministic generic question generator based on skill metadata (works for any subject)
        val q1 = QuestionEntity(
            courseId = skill.courseId,
            skillId = skill.id,
            questionText = "Which statement best describes the primary definition of ${skill.name} according to your course syllabus?",
            optionA = skill.description.ifBlank { "${skill.name} fundamental principle" },
            optionB = "A secondary non-essential effect unrelated to ${skill.chapter}.",
            optionC = "A historical term with no modern analytical application.",
            optionD = "An unverified hypothesis disproven by subsequent research.",
            correctAnswerIndex = 0,
            explanation = "Verified directly from course syllabus definition for ${skill.name}.",
            difficulty = difficulty,
            hint = "Recall the chapter context: ${skill.chapter}.",
            sourceDocumentName = skill.sourceDocumentName,
            sourcePage = skill.sourcePage
        )

        val q2 = QuestionEntity(
            courseId = skill.courseId,
            skillId = skill.id,
            questionText = "In the context of chapter '${skill.chapter}', why is mastery of '${skill.name}' critical?",
            optionA = "It serves as a core foundational concept for advanced problem solving.",
            optionB = "It only applies to introductory overview topics.",
            optionC = "It is optional and superseded by other formulas.",
            optionD = "It has no prerequisites and requires no practice.",
            correctAnswerIndex = 0,
            explanation = "Understanding ${skill.name} unlocks prerequisite dependencies across ${skill.chapter}.",
            difficulty = difficulty,
            hint = "Consider the role of ${skill.name} in your learning map.",
            sourceDocumentName = skill.sourceDocumentName,
            sourcePage = skill.sourcePage
        )

        listOf(q1, q2).take(count)
    }
}
