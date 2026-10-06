package com.example.ai.retrieval

import com.example.data.local.entity.DocumentChunkEntity
import kotlin.math.ln
import kotlin.math.max

data class RetrievedChunk(
    val chunk: DocumentChunkEntity,
    val score: Float,
    val matchedTerms: List<String>
)

class LocalRetriever {

    private val stopWords = setOf(
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "with",
        "of", "by", "from", "up", "about", "into", "through", "after", "over", "between",
        "out", "against", "during", "without", "before", "under", "around", "among",
        "is", "are", "was", "were", "be", "been", "being", "have", "has", "had",
        "do", "does", "did", "can", "could", "will", "would", "shall", "should",
        "what", "which", "who", "whom", "this", "that", "these", "those", "am", "it",
        "how", "why", "where", "when", "tell", "me", "explain", "give", "example"
    )

    fun search(
        query: String,
        chunks: List<DocumentChunkEntity>,
        topK: Int = 4
    ): List<RetrievedChunk> {
        if (chunks.isEmpty() || query.isBlank()) return emptyList()

        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) {
            return chunks.take(topK).map { RetrievedChunk(it, 0.1f, emptyList()) }
        }

        val totalDocs = chunks.size.toFloat()
        // Compute Document Frequency (DF) for each query term
        val docFrequency = mutableMapOf<String, Int>()
        val chunkTokensMap = chunks.associateWith { tokenize(it.text) }

        for (token in queryTokens) {
            var count = 0
            for ((_, tokens) in chunkTokensMap) {
                if (tokens.contains(token)) {
                    count++
                }
            }
            docFrequency[token] = count
        }

        val scoredChunks = chunks.mapNotNull { chunk ->
            val tokens = chunkTokensMap[chunk] ?: emptyList()
            if (tokens.isEmpty()) return@mapNotNull null

            var score = 0f
            val matchedTerms = mutableListOf<String>()

            // TF-IDF scoring with BM25 length normalization
            val docLen = tokens.size.toFloat()
            val avgDocLen = 80f // estimated average chunk length
            val k1 = 1.2f
            val b = 0.75f

            for (token in queryTokens) {
                val tf = tokens.count { it == token }
                if (tf > 0) {
                    matchedTerms.add(token)
                    val df = docFrequency[token] ?: 1
                    val idf = ln((totalDocs - df + 0.5f) / (df + 0.5f) + 1.0f)
                    val bm25Tf = (tf * (k1 + 1f)) / (tf + k1 * (1f - b + b * (docLen / avgDocLen)))
                    score += max(0.1f, idf * bm25Tf)
                }
            }

            // Exact phrase match bonus
            val cleanQuery = query.trim().lowercase()
            if (cleanQuery.length > 4 && chunk.text.lowercase().contains(cleanQuery)) {
                score += 5.0f
            }

            if (matchedTerms.isNotEmpty()) {
                RetrievedChunk(chunk = chunk, score = score, matchedTerms = matchedTerms)
            } else {
                null
            }
        }

        return scoredChunks
            .sortedByDescending { it.score }
            .take(topK)
    }

    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 && !stopWords.contains(it) }
    }
}
