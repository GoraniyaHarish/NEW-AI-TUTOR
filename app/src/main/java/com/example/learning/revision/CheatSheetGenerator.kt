package com.example.learning.revision

import com.example.ai.summarization.DocumentSummaryBuilder
import com.example.data.local.entity.DocumentChunkEntity
import com.example.data.local.entity.SkillEntity

data class CheatSheetData(
    val title: String,
    val sourceDoc: String,
    val sourcePage: Int,
    val keyPoints: List<String>,
    val formulas: List<String>,
    val definitions: List<String>,
    val sourceWarnings: List<String>,
    val hasReadableSource: Boolean
)

/** Builds a source-only revision sheet. No subject-specific facts are supplied by the app. */
object CheatSheetGenerator {
    private val formulaSignal = Regex("[=≤≥≈≠∝→←↔∫∑√Δπ±×÷]")
    private val definitionSignal = Regex("(?i)\\b(?:is defined as|means|refers to|is called|is known as|is the|are the)\\b")
    private val cautionSignal = Regex("(?i)\\b(?:common mistake|common error|do not|don't|avoid|incorrect|warning|pitfall|never)\\b")

    fun generateCheatSheet(skill: SkillEntity, chunks: List<DocumentChunkEntity>): CheatSheetData {
        val sourceChunks = chunks
            .filter { it.courseId == skill.courseId && it.text.isNotBlank() }
            .filter { skill.sourceDocumentId <= 0L || it.documentId == skill.sourceDocumentId }
            .sortedWith(compareBy<DocumentChunkEntity> { it.pageNumber }.thenBy { it.chunkIndex })
        val sourceDoc = sourceChunks.firstOrNull()?.sourceDocumentName
            ?: skill.sourceDocumentName.takeIf { it.isNotBlank() }
            ?: "No readable source selected"
        val sourcePage = sourceChunks.firstOrNull()?.pageNumber ?: skill.sourcePage
        val sentences = sourceChunks.flatMap { chunk ->
            chunk.text
                .replace('\n', ' ')
                .split(Regex("(?<=[.!?])\\s+"))
                .map(String::trim)
                .filter { it.length >= 18 }
        }.distinctBy { it.lowercase().replace(Regex("\\s+"), " ") }

        val keyPoints = DocumentSummaryBuilder.extractiveSummary(sourceChunks, maxPoints = 5)
            .map { it.text.trim() }
        return CheatSheetData(
            title = "${skill.name} Study Sheet",
            sourceDoc = sourceDoc,
            sourcePage = sourcePage,
            keyPoints = keyPoints,
            formulas = sentences.filter { formulaSignal.containsMatchIn(it) && it.length <= 240 }.take(8),
            definitions = sentences.filter { definitionSignal.containsMatchIn(it) }.take(8),
            sourceWarnings = sentences.filter { cautionSignal.containsMatchIn(it) }.take(6),
            hasReadableSource = sourceChunks.isNotEmpty()
        )
    }
}
