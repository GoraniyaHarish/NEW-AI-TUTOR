package com.example.ai.local

import com.example.ai.AIService
import com.example.ai.LessonExplanation
import com.example.ai.TutorResponse
import com.example.ai.nlp.QueryUnderstanding
import com.example.ai.nlp.TutorIntent
import com.example.ai.summarization.DocumentSummaryBuilder
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

        val topChunk = relevantChunks.firstOrNull { it.text.isNotBlank() }
        val hasEvidence = topChunk != null && topChunk.text.isNotBlank()

        // A skill record is metadata, not retrieved evidence. Never answer or cite as
        // document-grounded when retrieval returned no readable passage.
        if (!hasEvidence) {
            val missingAnswer = buildString {
                append("I couldn't find readable text in this course's imported documents for that request. Open Materials to check whether the document finished processing, or import a text-based PDF or TXT file.\n\n")
                append("Offline mode currently uses local document search and structured tutoring; an on-device neural model is not installed.\n\n")
                append("Your other courses stay separate; select the course that contains the document and try again.")
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

        if (analyzed.intent == TutorIntent.SUMMARY || analyzed.intent == TutorIntent.REVISE) {
            val summaryPoints = DocumentSummaryBuilder.extractiveSummary(relevantChunks, maxPoints = 8)
            if (summaryPoints.isNotEmpty()) {
                val documentNames = summaryPoints.map { it.sourceDocumentName }.distinct()
                val summaryText = buildString {
                    append("Summary from your uploaded material:\n\n")
                    summaryPoints.forEach { point ->
                        append("• ${point.text.trim()} (${point.sourceDocumentName}, page ${point.pageNumber})\n")
                    }
                    append("\nThese are key sentences extracted from the selected course material; no additional facts were added.")
                }
                return@withContext TutorResponse(
                    answer = summaryText,
                    sourceDocName = documentNames.singleOrNull(),
                    sourcePage = summaryPoints.firstOrNull()?.pageNumber.takeIf { documentNames.size == 1 },
                    isOffline = true,
                    confidence = 0.8f,
                    isGroundedInMaterial = true,
                    modelUsed = "offline-extractive-summary"
                )
            }
        }

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

        // 2. Grounded explanation constructed directly from retrieved material
        val evidenceChunk = requireNotNull(topChunk)
        val topicTitle = skill?.name ?: "Topic from ${evidenceChunk.sourceDocumentName}"
        val materialText = evidenceChunk.text

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
                    append("\n*(Extracted from ${evidenceChunk.sourceDocumentName}, Page ${evidenceChunk.pageNumber})*")
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

            append("\n\n*(Based on retrieved passage from ${evidenceChunk.sourceDocumentName}, page ${evidenceChunk.pageNumber}. On-device neural model not installed; showing structured offline notes analysis.)*")
        }

        TutorResponse(
            answer = responseText,
            sourceDocName = evidenceChunk.sourceDocumentName,
            sourcePage = evidenceChunk.pageNumber,
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

        val evidenceChunk = requireNotNull(topChunk)
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
            "Retrieved passage in ${evidenceChunk.sourceDocumentName} (Page ${evidenceChunk.pageNumber})."
        )

        LessonExplanation(
            title = skill.name,
            summary = summary,
            keyPoints = keyPoints,
            examples = examples,
            sourceDocName = evidenceChunk.sourceDocumentName,
            sourcePage = evidenceChunk.pageNumber
        )
    }

    override suspend fun generateQuestionsForSkill(
        skill: SkillEntity,
        count: Int,
        difficulty: String,
        relevantChunks: List<DocumentChunkEntity>
    ): List<QuestionEntity> = withContext(Dispatchers.Default) {
        if (count <= 0) return@withContext emptyList()

        // Build quiz items only from readable sentences in retrieved source chunks.
        // If the material does not contain enough distinct statements, return fewer
        // questions instead of inventing unsupported educational content.
        val sourceStatements = relevantChunks
            .filter { it.text.isNotBlank() }
            .flatMap { chunk ->
                val sentences = chunk.text
                    .split(Regex("(?<=[.!?])\\s+"))
                    .map { it.trim() }
                    .filter { it.length >= 15 }
                    .ifEmpty { listOf(chunk.text.trim()).filter { it.isNotBlank() } }
                sentences.map { sentence -> chunk to sentence }
            }
            .distinctBy { (chunk, sentence) -> "${chunk.id}:${sentence.lowercase()}" }
            .take(count)

        sourceStatements.mapIndexed { index, (evidenceChunk, sourceStatement) ->
            val statement = sourceStatement.take(280).trim()
            // Offline fallback must always supply four distinct, non-empty options.
            // Since the claim is extracted verbatim from the source, ask learners to
            // classify its evidence status instead of pretending we generated a
            // subject-matter MCQ or randomly marking a quoted fact as false.
            val correctLabel = "Explicitly supported by the material"
            val distractors = listOf(
                "Contradicted by the material",
                "Not mentioned in the material",
                "Only implied, not directly stated"
            )
            val seed = skill.id * 31L + count * 17L + evidenceChunk.id * 7L +
                difficulty.hashCode() + index * 97L
            val random = kotlin.random.Random(seed)
            val options = (distractors + correctLabel).shuffled(random)
            val correctIndex = options.indexOf(correctLabel)

            QuestionEntity(
                courseId = skill.courseId,
                skillId = skill.id,
                questionText = "What evidence status best describes this claim in the source?\n\n\"$statement\"",
                optionA = options[0],
                optionB = options[1],
                optionC = options[2],
                optionD = options[3],
                correctAnswerIndex = correctIndex,
                explanation = "The claim is quoted from page ${evidenceChunk.pageNumber} of ${evidenceChunk.sourceDocumentName}, so it is explicitly supported by the material.",
                difficulty = difficulty,
                hint = "Compare the claim with the cited passage on page ${evidenceChunk.pageNumber}.",
                sourceDocumentName = evidenceChunk.sourceDocumentName,
                sourcePage = evidenceChunk.pageNumber
            )
        }
    }
}
