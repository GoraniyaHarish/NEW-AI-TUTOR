package com.example.ai.cloud

import com.example.BuildConfig
import com.example.ai.AIService
import com.example.ai.LessonExplanation
import com.example.ai.TutorResponse
import com.example.ai.grounding.GroundingProvenanceValidator
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.DocumentChunkEntity
import com.example.data.local.entity.QuestionEntity
import com.example.data.local.entity.SkillEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

open class CloudAIService : AIService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    // Officially supported production Gemini models from Google AI Gemini specifications
    // Primary: gemini-3.5-flash-lite (fast, low latency)
    // Fallback: gemini-3.5-flash
    private val candidateModels = listOf(
        "gemini-3.5-flash-lite",
        "gemini-3.5-flash"
    )

    private val apiKey: String
        get() = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Exception) {
            ""
        }

    open fun isConfigured(): Boolean {
        val key = apiKey
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

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
            throw IllegalStateException("Cloud AI API key is not configured.")
        }

        val topChunk = relevantChunks.firstOrNull()
        val hasEvidence = relevantChunks.isNotEmpty() && topChunk != null && topChunk.text.isNotBlank()

        val contextText = if (hasEvidence) {
            relevantChunks.joinToString("\n\n---\n\n") { chunk ->
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
        }

        // Construct contents payload including recent conversation turns
        val contentsArray = JSONArray()

        // Include last 4 relevant turns of history
        val recentHistory = conversationHistory.takeLast(4)
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
            throw IllegalStateException("Cloud AI API key is not configured.")
        }

        val topChunk = relevantChunks.firstOrNull()
        val hasEvidence = relevantChunks.isNotEmpty() && topChunk != null && topChunk.text.isNotBlank()
        val docName = if (hasEvidence) topChunk.sourceDocumentName else skill.sourceDocumentName
        val pageNum = if (hasEvidence) topChunk.pageNumber else skill.sourcePage

        val contextText = if (hasEvidence) {
            relevantChunks.joinToString("\n\n") { it.text }
        } else {
            skill.description
        }

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

        // Real cloud call with no fabricated fallback text on error
        val rawText = executeGeminiRequest(requestJson).first

        val summary = rawText.substringAfter("SUMMARY:", "").substringBefore("KEY POINTS:").trim()
            .ifBlank { rawText.take(250) }

        val keyPointsBlock = rawText.substringAfter("KEY POINTS:", "").substringBefore("EXAMPLES:").trim()
        val keyPoints = keyPointsBlock.lines()
            .map { it.trim().removePrefix("-").removePrefix("•").trim() }
            .filter { it.isNotBlank() }
            .ifEmpty {
                listOf("Explanation derived from course materials for ${skill.name}.")
            }

        val examplesBlock = rawText.substringAfter("EXAMPLES:", "").trim()
        val examples = examplesBlock.lines()
            .map { it.trim().removePrefix("-").removePrefix("•").trim() }
            .filter { it.isNotBlank() }

        LessonExplanation(
            title = skill.name,
            summary = summary,
            keyPoints = keyPoints.take(5),
            examples = examples.take(3),
            sourceDocName = docName.ifBlank { "Course Material" },
            sourcePage = pageNum
        )
    }

    override suspend fun generateQuestionsForSkill(
        skill: SkillEntity,
        count: Int,
        difficulty: String
    ): List<QuestionEntity> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext emptyList()

        val prompt = buildString {
            append("Generate $count multiple-choice questions for the student skill '${skill.name}' at $difficulty difficulty.\n")
            append("Skill description: ${skill.description}\n\n")
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
                list.add(
                    QuestionEntity(
                        courseId = skill.courseId,
                        skillId = skill.id,
                        questionText = obj.getString("question"),
                        optionA = opts.optString(0, "A"),
                        optionB = opts.optString(1, "B"),
                        optionC = opts.optString(2, "C"),
                        optionD = opts.optString(3, "D"),
                        correctAnswerIndex = obj.getInt("correctIndex").coerceIn(0, 3),
                        explanation = obj.getString("explanation"),
                        difficulty = difficulty,
                        hint = obj.optString("hint", "Review definition of ${skill.name}"),
                        sourceDocumentName = skill.sourceDocumentName,
                        sourcePage = skill.sourcePage
                    )
                )
            }
            list.take(count)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun executeGeminiRequest(payload: JSONObject): Pair<String, String> {
        var lastException: Exception? = null

        for (model in candidateModels) {
            try {
                val text = callModelEndpoint(model, payload)
                return Pair(text, model)
            } catch (e: Exception) {
                lastException = e
            }
        }
        throw lastException ?: Exception("Failed to execute Gemini request across candidate models.")
    }

    private fun callModelEndpoint(modelName: String, payload: JSONObject): String {
        // SECURE: Send API key strictly in the 'x-goog-api-key' HTTP header, NEVER in URL query params
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"

        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Gemini API error ($modelName) HTTP ${response.code}: ${response.message}")
        }

        val body = response.body?.string() ?: throw Exception("Empty response body from Gemini API")
        val json = JSONObject(body)
        return json.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
    }
}
