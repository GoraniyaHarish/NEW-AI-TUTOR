package com.example.ai.cloud

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.example.ai.AIService
import com.example.ai.LessonExplanation
import com.example.ai.TutorResponse
import com.example.ai.grounding.GroundingProvenanceValidator
import com.example.ai.nlp.QueryUnderstanding
import com.example.ai.nlp.TutorIntent
import com.example.ai.summarization.DocumentSummaryBuilder
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.DocumentChunkEntity
import com.example.data.local.entity.QuestionEntity
import com.example.data.local.entity.SkillEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

open class CloudAIService(private val context: Context? = null) : AIService {

    // Use widely available Gemini Developer API model IDs. Try the fast/lower-cost
    // model first, then the standard Flash model if that model is unavailable.
    private val candidateModels = listOf(
        "gemini-2.5-flash-lite",
        "gemini-2.5-flash"
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .build()

    open fun isConfigured(): Boolean = BuildConfig.GEMINI_API_KEY.isNotBlank()

    override suspend fun answerTutor(
        query: String,
        skill: SkillEntity?,
        relevantChunks: List<DocumentChunkEntity>,
        courseId: Long
    ): TutorResponse = answerTutor(query, skill, relevantChunks, courseId, emptyList())

    open override suspend fun answerTutor(
        query: String,
        skill: SkillEntity?,
        relevantChunks: List<DocumentChunkEntity>,
        courseId: Long,
        conversationHistory: List<ChatMessageEntity>
    ): TutorResponse = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            throw IllegalStateException("Direct Gemini API key is missing. Add GEMINI_API_KEY to the build configuration.")
        }

        val topChunk = relevantChunks.firstOrNull()
        val hasEvidence = relevantChunks.isNotEmpty() && topChunk != null && topChunk.text.isNotBlank()
        val analyzedQuery = QueryUnderstanding.analyze(query, skill?.name)
        if ((analyzedQuery.intent == TutorIntent.SUMMARY || analyzedQuery.intent == TutorIntent.REVISE) && !hasEvidence) {
            throw IllegalStateException("No readable uploaded material is available to summarize for this course.")
        }

        val summaryContext = if (analyzedQuery.intent == TutorIntent.SUMMARY || analyzedQuery.intent == TutorIntent.REVISE) {
            DocumentSummaryBuilder.selectContext(relevantChunks)
        } else {
            DocumentSummaryBuilder.ContextSelection(relevantChunks, true, relevantChunks.sumOf { it.text.length })
        }

        val contextText = if (hasEvidence) {
            summaryContext.chunks.joinToString("\n\n---\n\n") { chunk ->
                "[Document: ${chunk.sourceDocumentName}, Page: ${chunk.pageNumber}]\n${chunk.text}"
            }
        } else {
            "No direct passage found in student's uploaded material."
        }

        val systemInstructionText = buildString {
            append("You are LearnMate, a supportive and accurate AI educational tutor.\n")
            append("Your goal is to help students learn effectively from their own uploaded course materials.\n\n")
            append("RULES:\n")
            append("1. Ground your answer in the provided STUDENT'S UPLOADED MATERIAL CONTEXT whenever available.\n")
            append("2. If the user's question CANNOT be answered from the provided material, explicitly begin with:\n")
            append("   \"This topic wasn't found in your uploaded materials. Based on general knowledge...\"\n")
            append("3. Teach clearly: explain the core concept, use a simple analogy if helpful, and ask a gentle follow-up check question.\n")
            append("4. NEVER invent documents, authors, or page numbers that are not in the context.\n")
            if (analyzedQuery.intent == TutorIntent.SUMMARY || analyzedQuery.intent == TutorIntent.REVISE) {
                append("5. Summarize the uploaded source passages themselves. Do not say the student has not uploaded a document when source context is present.\n")
                if (!summaryContext.isComplete) {
                    append("6. The provided passages are a sample spread across the course material because the full source exceeds the context budget. State that the summary covers selected passages, not the entire document.\n")
                }
            }
        }

        // Construct contents payload including recent conversation turns
        val contentsArray = JSONArray()

        // Include last 4 relevant turns of history
        val recentHistory = if (analyzedQuery.intent == TutorIntent.SUMMARY || analyzedQuery.intent == TutorIntent.REVISE) {
            emptyList()
        } else {
            conversationHistory.takeLast(4)
        }
        for (msg in recentHistory) {
            val role = if (msg.role == "user") "user" else "model"
            val turnObj = JSONObject().apply {
                put("role", role)
                put("parts", JSONArray().put(JSONObject().put("text", msg.content)))
            }
            contentsArray.put(turnObj)
        }

        // Current turn with material context
        val currentPrompt = buildString {
            if (skill != null) {
                append("Current Skill / Topic: ${skill.name} (Chapter: ${skill.chapter})\n\n")
            }
            append("STUDENT'S UPLOADED MATERIAL CONTEXT:\n")
            append(contextText)
            append("\n\nSTUDENT'S QUESTION:\n")
            append(query)
        }

        val userTurnObj = JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", currentPrompt)))
        }
        contentsArray.put(userTurnObj)

        val requestJson = JSONObject().apply {
            put("contents", contentsArray)
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemInstructionText)))
            })
        }

        val (responseText, usedModel) = executeGeminiRequest(requestJson)

        // Strict deterministic citation & provenance verification via production validator:
        // 1. If no evidence chunks were provided, citation MUST be null and isGroundedInMaterial MUST be false.
        // 2. If evidence chunks exist, verify that the model did not disclaim finding the material.
        val validation = GroundingProvenanceValidator.validate(responseText, relevantChunks)

        TutorResponse(
            answer = responseText,
            sourceDocName = validation.sourceDocumentName,
            sourcePage = validation.sourcePage,
            isOffline = false,
            confidence = if (validation.isGrounded) 0.95f else 0.85f,
            isGroundedInMaterial = validation.isGrounded,
            modelUsed = usedModel
        )
    }

    override suspend fun generateExplanation(
        skill: SkillEntity,
        relevantChunks: List<DocumentChunkEntity>
    ): LessonExplanation = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            throw IllegalStateException("Direct Gemini API key is missing. Add GEMINI_API_KEY to the build configuration.")
        }

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

        val contextText = relevantChunks.joinToString("\n\n") { it.text }

        val prompt = buildString {
            append("You are LearnMate. Create a structured educational lesson explanation for the skill: '${skill.name}'.\n\n")
            append("STUDENT MATERIAL CONTEXT:\n$contextText\n\n")
            append("Format your response strictly as:\n")
            append("SUMMARY: <3 sentence clear overview>\n")
            append("KEY POINTS:\n- <Point 1>\n- <Point 2>\n- <Point 3>\n- <Point 4>\n")
            append("EXAMPLES:\n- <Worked Example 1>\n- <Worked Example 2>\n")
        }

        val requestJson = JSONObject().apply {
            val contents = JSONArray()
            contents.put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            })
            put("contents", contents)
        }

        val rawText = executeGeminiRequest(requestJson).first

        val summary = rawText.substringAfter("SUMMARY:", "").substringBefore("KEY POINTS:").trim()
            .ifBlank { rawText.take(250) }

        val keyPointsBlock = rawText.substringAfter("KEY POINTS:", "").substringBefore("EXAMPLES:").trim()
        val keyPoints = keyPointsBlock.lines()
            .map { it.trim().removePrefix("-").removePrefix("•").trim() }
            .filter { it.isNotBlank() }

        val examplesBlock = rawText.substringAfter("EXAMPLES:", "").trim()
        val examples = examplesBlock.lines()
            .map { it.trim().removePrefix("-").removePrefix("•").trim() }
            .filter { it.isNotBlank() }

        LessonExplanation(
            title = skill.name,
            summary = summary,
            keyPoints = keyPoints.take(5),
            examples = examples.take(3),
            sourceDocName = topChunk.sourceDocumentName,
            sourcePage = topChunk.pageNumber
        )
    }

    override suspend fun generateQuestionsForSkill(
        skill: SkillEntity,
        count: Int,
        difficulty: String,
        relevantChunks: List<DocumentChunkEntity>
    ): List<QuestionEntity> = withContext(Dispatchers.IO) {
        if (!isConfigured() || relevantChunks.isEmpty()) return@withContext emptyList()

        val topChunk = relevantChunks.firstOrNull { it.text.isNotBlank() } ?: return@withContext emptyList()
        val contextText = relevantChunks.joinToString("\n\n") { chunk ->
            "[Document: ${chunk.sourceDocumentName}, Page: ${chunk.pageNumber}]\n${chunk.text}"
        }

        val prompt = buildString {
            append("Generate $count multiple-choice questions grounded strictly in the provided document context for skill '${skill.name}' at $difficulty difficulty.\n\n")
            append("Context:\n$contextText\n\n")
            append("Return valid JSON format matching this schema:\n")
            append("[\n")
            append("  {\n")
            append("    \"question\": \"Question text?\",\n")
            append("    \"options\": [\"Option A\", \"Option B\", \"Option C\", \"Option D\"],\n")
            append("    \"correctIndex\": 0,\n")
            append("    \"explanation\": \"Why this option is correct\",\n")
            append("    \"hint\": \"Helpful hint\"\n")
            append("  }\n")
            append("]\n")
            append("Only return JSON array, no markdown markers.")
        }

        val requestJson = JSONObject().apply {
            val contents = JSONArray()
            contents.put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            })
            put("contents", contents)
        }

        try {
            val (text, _) = executeGeminiRequest(requestJson)
            val cleanJson = text.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val array = JSONArray(cleanJson)
            val list = mutableListOf<QuestionEntity>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val opts = obj.getJSONArray("options")
                val options = (0 until opts.length()).map { opts.optString(it).trim() }
                val correctIndex = obj.optInt("correctIndex", -1)
                val questionText = obj.optString("question").trim()
                val explanation = obj.optString("explanation").trim()
                if (options.size != 4 || options.any(String::isBlank) || options.distinct().size != 4 ||
                    correctIndex !in 0..3 || questionText.isBlank() || explanation.isBlank()
                ) {
                    return@withContext emptyList()
                }
                list.add(
                    QuestionEntity(
                        courseId = skill.courseId,
                        skillId = skill.id,
                        questionText = questionText,
                        optionA = options[0],
                        optionB = options[1],
                        optionC = options[2],
                        optionD = options[3],
                        correctAnswerIndex = correctIndex,
                        explanation = explanation,
                        difficulty = difficulty,
                        hint = obj.optString("hint", "Review page ${topChunk.pageNumber}"),
                        sourceDocumentName = topChunk.sourceDocumentName,
                        sourcePage = topChunk.pageNumber
                    )
                )
            }
            list.take(count)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun executeGeminiRequest(payload: JSONObject): Pair<String, String> {
        if (!isConfigured()) throw IllegalStateException("Gemini API key is missing from this build.")
        var lastException: Exception? = null
        for (model in candidateModels) {
            try {
                return callGeminiApi(model, payload) to model
            } catch (e: Exception) {
                Log.w(TAG, "Direct Gemini API request failed for $model: ${e.message}")
                // Invalid keys, permission failures, and malformed requests will not
                // improve by trying a second model; fail fast instead.
                if (e is GeminiHttpException && e.code in listOf(400, 401, 403)) throw e
                if (lastException == null) lastException = e else lastException.addSuppressed(e)
            }
        }
        throw lastException ?: IllegalStateException("All Gemini model requests failed.")
    }

    private suspend fun callGeminiApi(modelName: String, payload: JSONObject): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent")
            .header("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching { JSONObject(bodyText).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty()
                throw GeminiHttpException(
                    response.code,
                    "Gemini API HTTP ${response.code}: ${detail.ifBlank { "request failed" }}"
                )
            }
            val parts = JSONObject(bodyText).optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
                ?: throw IllegalStateException("Gemini API returned no text parts.")
            val text = (0 until parts.length()).mapNotNull { parts.optJSONObject(it)?.optString("text") }
                .filter { it.isNotBlank() }.joinToString("\n")
            text.ifBlank { throw IllegalStateException("Gemini API returned an empty response.") }
        }
    }
    private class GeminiHttpException(val code: Int, message: String) : IllegalStateException(message)

    private companion object {
        const val TAG = "LearnMateCloudAI"
    }
}
