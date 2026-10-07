package com.example.ai.retrieval

import com.example.data.local.entity.DocumentChunkEntity
import java.util.Locale
import kotlin.math.ln
import kotlin.math.max

/**
 * Represents a single retrieved chunk with its deterministic relevance score,
 * matched query terms, and direct provenance to the underlying DocumentChunkEntity.
 */
data class RetrievedChunk(
    val chunk: DocumentChunkEntity,
    val score: Float,
    val matchedTerms: List<String>
) {
    val documentId: Long get() = chunk.documentId
    val documentName: String get() = chunk.sourceDocumentName
    val pageNumber: Int get() = chunk.pageNumber
    val chunkIndex: Int get() = chunk.chunkIndex
    val text: String get() = chunk.text
}

/**
 * Deterministic query normalizer and tokenizer for on-device lexical search.
 * Strips conversational filler, normalizes punctuation and morphological suffixes,
 * and extracts salient keywords without calling any cloud or neural models.
 */
object QueryNormalizer {

    // Conversational query prefixes to strip so natural language queries match the underlying concept
    private val CONVERSATIONAL_PATTERNS = listOf(
        Regex("(?i)^\\s*(?:can\\s+you\\s+)?(?:please\\s+)?explain\\s+(?:to\\s+me\\s+)?(?:about\\s+)?"),
        Regex("(?i)^\\s*(?:can\\s+you\\s+)?(?:please\\s+)?tell\\s+me\\s+(?:about\\s+)?"),
        Regex("(?i)^\\s*(?:can\\s+you\\s+)?(?:please\\s+)?teach\\s+me\\s+(?:about\\s+)?"),
        Regex("(?i)^\\s*(?:can\\s+you\\s+)?(?:please\\s+)?help\\s+me\\s+understand\\s+(?:about\\s+)?"),
        Regex("(?i)^\\s*i\\s+(?:do\\s*not|don't)\\s+understand\\s+(?:about\\s+)?"),
        Regex("(?i)^\\s*what\\s+(?:is|are|was|were|do\\s+you\\s+mean\\s+by)\\s+(?:the\\s+concept\\s+of\\s+)?(?:a\\s+|an\\s+|the\\s+)?"),
        Regex("(?i)^\\s*how\\s+(?:does|do|can|to|works?)\\s+"),
        Regex("(?i)^\\s*why\\s+(?:is|are|does|do)\\s+"),
        Regex("(?i)^\\s*(?:give\\s+me\\s+an?\\s+example\\s+of|define|describe|clarify)\\s+(?:the\\s+concept\\s+of\\s+)?(?:a\\s+|an\\s+|the\\s+)?")
    )

    val STOP_WORDS = setOf(
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "with",
        "of", "by", "from", "up", "about", "into", "through", "after", "over", "between",
        "out", "against", "during", "without", "before", "under", "around", "among",
        "is", "are", "was", "were", "be", "been", "being", "have", "has", "had",
        "do", "does", "did", "can", "could", "will", "would", "shall", "should",
        "what", "which", "who", "whom", "this", "that", "these", "those", "am", "it",
        "how", "why", "where", "when", "tell", "me", "explain", "give", "example",
        "please", "you", "your", "my", "i", "we", "our", "us", "they", "them",
        "just", "also", "very", "much", "more", "most", "some", "any", "no", "not"
    )

    /**
     * Strips common natural-language question framing while keeping core semantic query text.
     */
    fun stripConversationalFraming(rawQuery: String): String {
        var cleaned = rawQuery.trim()
        for (pattern in CONVERSATIONAL_PATTERNS) {
            val match = pattern.find(cleaned)
            if (match != null) {
                cleaned = cleaned.substring(match.range.last + 1).trim()
                break
            }
        }
        return cleaned.trimEnd('?', '.', '!', ':', ';', ',')
    }

    /**
     * Tokenizes text into normalized lowercase tokens.
     * Preserves Unicode letters, numbers, and key symbols, removes punctuation,
     * applies light morphological suffix normalization, and filters out stop words.
     */
    fun tokenize(text: String, filterStopWords: Boolean = true): List<String> {
        if (text.isBlank()) return emptyList()

        val tokens = mutableListOf<String>()
        val current = StringBuilder()

        for (ch in text) {
            if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' || ch in '\u0370'..'\u03FF' || ch in '\u2200'..'\u22FF') {
                current.append(ch.lowercaseChar())
            } else {
                if (current.isNotEmpty()) {
                    val token = normalizeStem(current.toString().trim('-', '_'))
                    if (token.isNotBlank()) {
                        tokens.add(token)
                    }
                    current.clear()
                }
            }
        }
        if (current.isNotEmpty()) {
            val token = normalizeStem(current.toString().trim('-', '_'))
            if (token.isNotBlank()) {
                tokens.add(token)
            }
        }

        return if (filterStopWords) {
            tokens.filter { it.length >= 2 && !STOP_WORDS.contains(it) }
        } else {
            tokens.filter { it.isNotBlank() }
        }
    }

    /**
     * Deterministic, lightweight suffix normalization (stemming) for common English plurals and inflections.
     * e.g., "recursions" -> "recursion", "algorithms" -> "algorithm", "matrices" -> "matrix", "running" -> "run"
     */
    fun normalizeStem(word: String): String {
        val w = word.lowercase(Locale.ROOT)
        if (w.length <= 3) return w

        return when {
            w.endsWith("ies") && w.length > 4 -> w.dropLast(3) + "y"
            w.endsWith("ices") && w.length > 4 -> w.dropLast(4) + "ix"
            w.endsWith("sses") -> w.dropLast(2)
            w.endsWith("ches") || w.endsWith("shes") || w.endsWith("xes") || w.endsWith("zes") -> w.dropLast(2)
            w.endsWith("ing") && w.length > 5 -> {
                val base = w.dropLast(3)
                if (base.length > 2 && base.last() == base[base.length - 2]) base.dropLast(1) else base
            }
            w.endsWith("ed") && w.length > 4 -> {
                val base = w.dropLast(2)
                if (base.length > 2 && base.last() == base[base.length - 2]) base.dropLast(1) else base
            }
            w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") && !w.endsWith("is") -> w.dropLast(1)
            else -> w
        }
    }
}

/**
 * Production-ready, deterministic, 100% offline lexical retrieval engine.
 * Searches real DocumentChunks using BM25 ranking, phrase boosting, title weighting,
 * and term coverage scoring with absolute provenance preservation.
 */
class LocalRetriever(
    private val k1: Float = 1.2f,
    private val b: Float = 0.75f,
    private val minRelevanceThreshold: Float = 0.5f
) {

    /**
     * Searches a collection of DocumentChunks for relevance against a user query.
     *
     * @param query The natural language or keyword query
     * @param chunks The corpus of document chunks to search
     * @param courseId Optional course filter to strictly enforce course isolation
     * @param documentId Optional document filter
     * @param topK Maximum number of results to return
     * @param minScore Minimum relevance score required to be included in results
     * @return Ranked list of RetrievedChunk objects ordered strictly descending by score
     */
    fun search(
        query: String,
        chunks: List<DocumentChunkEntity>,
        courseId: Long? = null,
        documentId: Long? = null,
        topK: Int = 4,
        minScore: Float = minRelevanceThreshold
    ): List<RetrievedChunk> {
        if (chunks.isEmpty() || query.isBlank()) return emptyList()

        // 1. Enforce Course and Document Isolation
        val scopedChunks = chunks.filter { chunk ->
            (courseId == null || chunk.courseId == courseId) &&
            (documentId == null || chunk.documentId == documentId)
        }
        if (scopedChunks.isEmpty()) return emptyList()

        // 2. Query Normalization & Tokenization
        val strippedQuery = QueryNormalizer.stripConversationalFraming(query)
        val queryTokens = QueryNormalizer.tokenize(strippedQuery)
        val fallbackTokens = if (queryTokens.isEmpty()) QueryNormalizer.tokenize(query) else queryTokens

        // If query has no valid search tokens (e.g. pure punctuation or pure stop words), return empty
        if (fallbackTokens.isEmpty()) return emptyList()

        val uniqueQueryTerms = fallbackTokens.distinct()
        val totalDocs = scopedChunks.size.toFloat()

        // 3. Pre-tokenize all scoped chunks and calculate corpus statistics
        val chunkTokensMap = mutableMapOf<Long, List<String>>()
        var totalTokensInCorpus = 0L

        for (chunk in scopedChunks) {
            val tokens = QueryNormalizer.tokenize(chunk.text)
            chunkTokensMap[chunk.id] = tokens
            totalTokensInCorpus += tokens.size
        }

        val avgDocLen = if (scopedChunks.isNotEmpty()) {
            max(10f, totalTokensInCorpus.toFloat() / scopedChunks.size.toFloat())
        } else {
            80f
        }

        // 4. Compute Document Frequency (DF) for each query term in the scoped corpus
        val docFrequency = mutableMapOf<String, Int>()
        for (term in uniqueQueryTerms) {
            var df = 0
            for (chunk in scopedChunks) {
                val tokens = chunkTokensMap[chunk.id] ?: emptyList()
                if (tokens.contains(term)) {
                    df++
                }
            }
            docFrequency[term] = df
        }

        // 5. Compute deterministic BM25 scores with phrase and header bonuses
        val scoredResults = mutableListOf<RetrievedChunk>()
        val cleanLowerQuery = query.trim().lowercase(Locale.ROOT)
        val cleanStrippedLower = strippedQuery.lowercase(Locale.ROOT)

        for (chunk in scopedChunks) {
            val tokens = chunkTokensMap[chunk.id] ?: emptyList()
            if (tokens.isEmpty()) continue

            val docLen = tokens.size.toFloat()
            var bm25Score = 0f
            val matchedTerms = mutableListOf<String>()

            for (term in uniqueQueryTerms) {
                val tf = tokens.count { it == term }
                if (tf > 0) {
                    matchedTerms.add(term)
                    val df = docFrequency[term] ?: 1
                    // Standard Robertson-Spärck Jones BM25 IDF with smoothing
                    val idf = ln(1.0f + (totalDocs - df + 0.5f) / (df + 0.5f))
                    val tfComponent = (tf * (k1 + 1f)) / (tf + k1 * (1f - b + b * (docLen / avgDocLen)))
                    bm25Score += max(0.05f, idf * tfComponent)
                }
            }

            // Zero matches from query terms -> skip chunk completely
            if (matchedTerms.isEmpty()) continue

            var totalScore = bm25Score

            // Exact phrase match bonus in chunk text
            val lowerChunkText = chunk.text.lowercase(Locale.ROOT)
            if (cleanStrippedLower.length >= 4 && lowerChunkText.contains(cleanStrippedLower)) {
                totalScore += 3.5f
            } else if (cleanLowerQuery.length >= 4 && lowerChunkText.contains(cleanLowerQuery)) {
                totalScore += 2.5f
            }

            // Document title / filename match bonus
            val lowerDocName = chunk.sourceDocumentName.lowercase(Locale.ROOT)
            val docNameMatches = uniqueQueryTerms.count { lowerDocName.contains(it) }
            if (docNameMatches > 0) {
                totalScore += (docNameMatches * 0.8f)
            }

            // Heading / First-line match bonus
            val firstLine = chunk.text.lines().firstOrNull()?.lowercase(Locale.ROOT) ?: ""
            val firstLineMatches = uniqueQueryTerms.count { firstLine.contains(it) }
            if (firstLineMatches > 0) {
                totalScore += (firstLineMatches * 1.2f)
            }

            // Term coverage multiplier (rewards chunks containing ALL query terms over single repeated terms)
            val coverageRatio = matchedTerms.size.toFloat() / uniqueQueryTerms.size.toFloat()
            totalScore *= (0.6f + (0.4f * coverageRatio))

            // Only retain chunks that meet the minimum relevance threshold
            if (totalScore >= minScore) {
                scoredResults.add(
                    RetrievedChunk(
                        chunk = chunk,
                        score = totalScore,
                        matchedTerms = matchedTerms
                    )
                )
            }
        }

        // 6. Deterministic Sort: primary descending by score, tie-break by documentId, pageNumber, chunkIndex, id
        return scoredResults
            .sortedWith(
                compareByDescending<RetrievedChunk> { it.score }
                    .thenBy { it.chunk.documentId }
                    .thenBy { it.chunk.pageNumber }
                    .thenBy { it.chunk.chunkIndex }
                    .thenBy { it.chunk.id }
            )
            .take(topK)
    }
}
