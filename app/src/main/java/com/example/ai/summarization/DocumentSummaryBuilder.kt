package com.example.ai.summarization

import com.example.ai.retrieval.QueryNormalizer
import com.example.data.local.entity.DocumentChunkEntity
import kotlin.math.ln

/** Bounded, provenance-preserving document context and extractive summary helpers. */
object DocumentSummaryBuilder {
    const val DEFAULT_CONTEXT_CHAR_BUDGET = 48_000

    data class ContextSelection(
        val chunks: List<DocumentChunkEntity>,
        val isComplete: Boolean,
        val originalCharacterCount: Int
    )

    fun selectContext(
        chunks: List<DocumentChunkEntity>,
        maxCharacters: Int = DEFAULT_CONTEXT_CHAR_BUDGET
    ): ContextSelection {
        val ordered = chunks
            .filter { it.text.isNotBlank() }
            .sortedWith(compareBy<DocumentChunkEntity> { it.documentId }.thenBy { it.pageNumber }.thenBy { it.chunkIndex })
        val originalCharacters = ordered.sumOf { it.text.length }
        if (originalCharacters <= maxCharacters) {
            return ContextSelection(ordered, true, originalCharacters)
        }

        val targetCount = maxOf(1, maxCharacters / maxOf(1, originalCharacters / ordered.size))
            .coerceAtMost(ordered.size)
        val sampled = if (ordered.size == 1 || targetCount == 1) {
            listOf(ordered[ordered.lastIndex / 2])
        } else {
            (0 until targetCount).map { index ->
                ordered[index * (ordered.lastIndex) / (targetCount - 1)]
            }.distinctBy { it.id }
        }

        val bounded = mutableListOf<DocumentChunkEntity>()
        var remaining = maxCharacters
        for (chunk in sampled) {
            if (remaining <= 0) break
            val text = if (chunk.text.length <= remaining) chunk.text else chunk.text.take(remaining)
            if (text.isNotBlank()) bounded.add(chunk.copy(text = text))
            remaining -= text.length
        }
        return ContextSelection(bounded, false, originalCharacters)
    }

    /** Returns literal source sentences ranked by corpus salience; it does not invent prose. */
    fun extractiveSummary(chunks: List<DocumentChunkEntity>, maxPoints: Int = 8): List<DocumentChunkEntity> {
        if (maxPoints <= 0) return emptyList()
        data class Sentence(val text: String, val chunk: DocumentChunkEntity, val index: Int, val terms: Set<String>)

        val sentences = chunks
            .sortedWith(compareBy<DocumentChunkEntity> { it.documentId }.thenBy { it.pageNumber }.thenBy { it.chunkIndex })
            .flatMap { chunk ->
                chunk.text
                    .replace('\n', ' ')
                    .split(Regex("(?<=[.!?])\\s+"))
                    .map(String::trim)
                    .filter { it.length >= 28 }
                    .mapIndexed { index, text -> Sentence(text, chunk, index, QueryNormalizer.tokenize(text).toSet()) }
            }
            .distinctBy { it.text.lowercase().replace(Regex("\\s+"), " ") }
        if (sentences.isEmpty()) return emptyList()

        val documentFrequency = mutableMapOf<String, Int>()
        sentences.forEach { sentence -> sentence.terms.forEach { term -> documentFrequency[term] = (documentFrequency[term] ?: 0) + 1 } }
        val scored = sentences.map { sentence ->
            val salience = sentence.terms.sumOf { term ->
                ln(1.0 + sentences.size.toDouble() / (documentFrequency[term] ?: 1))
            } / maxOf(1, sentence.terms.size)
            sentence to salience
        }.sortedWith(compareByDescending<Pair<Sentence, Double>> { it.second }
            .thenBy { it.first.chunk.documentId }
            .thenBy { it.first.chunk.pageNumber }
            .thenBy { it.first.index })

        val selected = mutableListOf<Sentence>()
        val usedPages = mutableSetOf<Pair<Long, Int>>()
        for ((sentence, _) in scored) {
            if (selected.size >= maxPoints) break
            val page = sentence.chunk.documentId to sentence.chunk.pageNumber
            if (usedPages.add(page)) selected.add(sentence)
        }
        if (selected.size < maxPoints) {
            for ((sentence, _) in scored) {
                if (selected.size >= maxPoints) break
                if (selected.none { it.text.equals(sentence.text, ignoreCase = true) }) selected.add(sentence)
            }
        }

        val ordered = selected.sortedWith(compareBy<Sentence> { it.chunk.documentId }.thenBy { it.chunk.pageNumber }.thenBy { it.index })
        return ordered.map { it.chunk.copy(text = it.text) }
    }
}
