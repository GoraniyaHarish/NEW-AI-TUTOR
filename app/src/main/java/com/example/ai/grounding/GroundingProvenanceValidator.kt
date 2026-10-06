package com.example.ai.grounding

import com.example.data.local.entity.DocumentChunkEntity

data class GroundingValidationResult(
    val isGrounded: Boolean,
    val sourceDocumentName: String?,
    val sourcePage: Int?
)

/**
 * Deterministic post-processing and provenance verification helper.
 * Ensures citations and grounded flags strictly originate from verified retrieved chunks.
 */
object GroundingProvenanceValidator {

    private val MISSING_MATERIAL_DISCLAIMERS = listOf(
        "This topic wasn't found in your uploaded materials",
        "not found in your uploaded materials",
        "not found in your uploaded notes",
        "not found in your uploaded study materials",
        "Based on general knowledge"
    )

    fun validate(
        responseText: String,
        relevantChunks: List<DocumentChunkEntity>
    ): GroundingValidationResult {
        val topChunk = relevantChunks.firstOrNull()
        val hasEvidence = topChunk != null && topChunk.text.isNotBlank()

        if (!hasEvidence || topChunk == null) {
            return GroundingValidationResult(
                isGrounded = false,
                sourceDocumentName = null,
                sourcePage = null
            )
        }

        // Detect if model explicitly disclaimed finding material
        val hasDisclaim = MISSING_MATERIAL_DISCLAIMERS.any { disclaimer ->
            responseText.contains(disclaimer, ignoreCase = true)
        }

        if (hasDisclaim) {
            return GroundingValidationResult(
                isGrounded = false,
                sourceDocumentName = null,
                sourcePage = null
            )
        }

        // Grounded: Citation metadata comes ONLY from verified retrieved chunk metadata
        return GroundingValidationResult(
            isGrounded = true,
            sourceDocumentName = topChunk.sourceDocumentName,
            sourcePage = topChunk.pageNumber
        )
    }
}
