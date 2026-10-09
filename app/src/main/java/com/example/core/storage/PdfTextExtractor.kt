package com.example.core.storage

import android.content.Context
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.InputStream
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

data class ExtractedPage(
    val pageNumber: Int,
    val text: String
)

data class ExtractedChunk(
    val pageNumber: Int,
    val chunkIndex: Int,
    val text: String
)

sealed class IngestionResult {
    data class Success(val chunks: List<ExtractedChunk>, val pageCount: Int) : IngestionResult()
    data class Failure(val reason: String, val isRecoverable: Boolean = false) : IngestionResult()
}

/**
 * Deterministic educational text normalizer and chunker.
 * Operates purely on-device without invoking any LLM.
 */
object DocumentTextProcessor {

    /** Maximum allowed raw input size (50 MB) */
    const val MAX_FILE_SIZE_BYTES = 50L * 1024L * 1024L

    /** Target chunk size in approximate tokens (400–600 tokens; ~4 characters per token = ~1600-2400 chars) */
    const val TARGET_CHUNK_MIN_CHARS = 1600
    const val TARGET_CHUNK_MAX_CHARS = 2400

    /** Overlap size in approximate tokens (~50 tokens; ~200 chars) */
    const val OVERLAP_CHARS = 200

    /**
     * Sanitizes raw document text:
     * - Normalizes CRLF / CR line endings to standard LF
     * - Replaces non-breaking and unusual spaces with standard spaces
     * - Strips unprintable control characters (retaining newlines and tabs)
     * - Collapses 3+ consecutive newlines to double newlines (preserving paragraph structure)
     * - Strips redundant trailing/leading whitespace per line
     * - Preserves genuine mathematical and educational symbols
     */
    fun normalizeText(raw: String): String {
        if (raw.isBlank()) return ""

        val lineEndingNormalized = raw
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace('\u00A0', ' ')
            .replace('\u2007', ' ')
            .replace('\u202F', ' ')
            .replace('\uFEFF', ' ')

        val cleanChars = StringBuilder(lineEndingNormalized.length)
        for (ch in lineEndingNormalized) {
            if (ch == '\n' || ch == '\t' || (ch.code in 32..126) || ch.code >= 128) {
                cleanChars.append(ch)
            }
        }

        val cleanedLines = cleanChars.toString().lines().map { it.trimEnd() }
        val joined = cleanedLines.joinToString("\n")

        // Collapse excessive blank lines
        return joined.replace(Regex("\n{3,}"), "\n\n").trim()
    }

    /**
     * Chunks normalized multi-page text into deterministic, non-empty chunks
     * with approximate token windowing (400–600 tokens, ~50-token overlap).
     * Preserves page provenance and sequence order.
     */
    fun chunkPages(pages: List<ExtractedPage>): List<ExtractedChunk> {
        val result = mutableListOf<ExtractedChunk>()
        var globalChunkIndex = 0

        for (page in pages) {
            val normalizedPageText = normalizeText(page.text)
            if (normalizedPageText.isBlank()) continue

            val paragraphs = normalizedPageText.split(Regex("\n\n+")).map { it.trim() }.filter { it.isNotEmpty() }
            if (paragraphs.isEmpty()) continue

            var currentChunk = StringBuilder()
            var previousParagraphTail = ""

            for (p in paragraphs) {
                // If adding this paragraph exceeds our upper limit, flush current chunk
                if (currentChunk.isNotEmpty() && (currentChunk.length + p.length > TARGET_CHUNK_MAX_CHARS)) {
                    val chunkText = currentChunk.toString().trim()
                    if (chunkText.isNotEmpty()) {
                        result.add(
                            ExtractedChunk(
                                pageNumber = page.pageNumber,
                                chunkIndex = globalChunkIndex++,
                                text = chunkText
                            )
                        )
                    }

                    // Extract overlap from tail of currentChunk
                    previousParagraphTail = if (chunkText.length > OVERLAP_CHARS) {
                        val tailSnippet = chunkText.takeLast(OVERLAP_CHARS).trim()
                        val lastSentenceOrWord = tailSnippet.substringAfter(' ', tailSnippet)
                        "... $lastSentenceOrWord"
                    } else {
                        chunkText
                    }

                    currentChunk = StringBuilder()
                    if (previousParagraphTail.isNotBlank()) {
                        currentChunk.append(previousParagraphTail).append("\n\n")
                    }
                }

                // If single paragraph is extremely large, slice it into target windows
                if (p.length > TARGET_CHUNK_MAX_CHARS) {
                    val sentences = p.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
                    for (sentence in sentences) {
                        if (currentChunk.isNotEmpty() && (currentChunk.length + sentence.length > TARGET_CHUNK_MAX_CHARS)) {
                            val chunkText = currentChunk.toString().trim()
                            if (chunkText.isNotEmpty()) {
                                result.add(
                                    ExtractedChunk(
                                        pageNumber = page.pageNumber,
                                        chunkIndex = globalChunkIndex++,
                                        text = chunkText
                                    )
                                )
                            }
                            currentChunk = StringBuilder()
                        }
                        currentChunk.append(sentence).append(" ")
                    }
                } else {
                    currentChunk.append(p).append("\n\n")
                }
            }

            // Flush remaining text for this page
            if (currentChunk.isNotBlank()) {
                val chunkText = currentChunk.toString().trim()
                if (chunkText.isNotEmpty()) {
                    result.add(
                        ExtractedChunk(
                            pageNumber = page.pageNumber,
                            chunkIndex = globalChunkIndex++,
                            text = chunkText
                        )
                    )
                }
            }
        }

        return result
    }
}

/**
 * Production Document & PDF Text Extractor.
 * Extracts real text, enforces security boundaries, rejects empty or corrupt inputs,
 * and maintains strict page provenance without fabricating educational content.
 */
class PdfTextExtractor(private val context: Context) {

    /**
     * Extracts text from Uri with complete validation:
     * - File accessibility check
     * - File size boundary check (<= 50 MB)
     * - Stream extraction per file format
     */
    fun extractTextFromUri(uri: Uri, fileName: String, explicitSizeBytes: Long? = null): IngestionResult {
        val extension = fileName.substringAfterLast('.', "").lowercase()

        // 1. Validate file size if known or check via openInputStream
        if (explicitSizeBytes != null && explicitSizeBytes > DocumentTextProcessor.MAX_FILE_SIZE_BYTES) {
            return IngestionResult.Failure(
                reason = "File '$fileName' exceeds maximum permitted size of 50 MB.",
                isRecoverable = false
            )
        }

        val inputStream = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            null
        }

        if (inputStream == null) {
            return IngestionResult.Failure(
                reason = "Unable to access file '$fileName'. The file may have been moved or permission was revoked.",
                isRecoverable = false
            )
        }

        return when (extension) {
            "txt", "text", "md", "csv" -> extractFromPlainTextStream(inputStream)
            "pdf" -> extractFromPdfStream(inputStream, fileName)
            else -> extractFromPlainTextStream(inputStream)
        }
    }

    fun extractFromPlainTextStream(inputStream: InputStream): IngestionResult {
        val content = try {
            inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            return IngestionResult.Failure("Failed to read text stream: ${e.localizedMessage ?: "I/O error"}")
        }

        if (content.isBlank()) {
            return IngestionResult.Failure("Document is empty and contains no readable text.")
        }

        val pages = listOf(ExtractedPage(pageNumber = 1, text = content))
        val chunks = DocumentTextProcessor.chunkPages(pages)

        if (chunks.isEmpty()) {
            return IngestionResult.Failure("Document contains only blank or unprintable whitespace.")
        }

        return IngestionResult.Success(chunks = chunks, pageCount = 1)
    }

    fun extractFromPdfStream(inputStream: InputStream, fileName: String): IngestionResult {
        val bytes = try {
            inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            return IngestionResult.Failure("Failed to read PDF bytes: ${e.localizedMessage ?: "I/O error"}")
        }

        if (bytes.size > DocumentTextProcessor.MAX_FILE_SIZE_BYTES) {
            return IngestionResult.Failure("PDF '$fileName' exceeds the 50 MB limit.")
        }

        if (bytes.isEmpty()) {
            return IngestionResult.Failure("PDF '$fileName' is 0 bytes (empty file).")
        }

        val extractedPages = parsePdfBytes(bytes)
        val validPages = extractedPages.filter { it.text.isNotBlank() }

        if (validPages.isEmpty()) {
            return IngestionResult.Failure(
                reason = "Could not extract readable text from '$fileName'. The PDF may be scanned, image-only, or encrypted.",
                isRecoverable = false
            )
        }

        val chunks = DocumentTextProcessor.chunkPages(validPages)
        if (chunks.isEmpty()) {
            return IngestionResult.Failure("No readable text chunks could be extracted from '$fileName'.")
        }

        val totalPages = extractedPages.maxOfOrNull { it.pageNumber } ?: validPages.size
        return IngestionResult.Success(chunks = chunks, pageCount = totalPages)
    }

    /**
     * Backward-compatible convenience method for plain text input stream
     */
    fun extractFromPlainText(inputStream: InputStream): List<ExtractedChunk> {
        return when (val res = extractFromPlainTextStream(inputStream)) {
            is IngestionResult.Success -> res.chunks
            is IngestionResult.Failure -> emptyList()
        }
    }

    /**
     * Backward-compatible convenience method for PDF input stream
     */
    fun extractFromPdf(inputStream: InputStream, fileName: String): List<ExtractedChunk> {
        return when (val res = extractFromPdfStream(inputStream, fileName)) {
            is IngestionResult.Success -> res.chunks
            is IngestionResult.Failure -> emptyList()
        }
    }

    /**
     * Uses PDFBox-Android to parse real PDF object structures, compressed streams,
     * encoded text, and per-page text. Scanned/image-only PDFs still require OCR,
     * which is not included in this build.
     */
    private fun parsePdfBytes(bytes: ByteArray): List<ExtractedPage> {
        return try {
            PDFBoxResourceLoader.init(context)
            PDDocument.load(ByteArrayInputStream(bytes)).use { document ->
                (1..document.numberOfPages).map { pageNumber ->
                    val stripper = PDFTextStripper().apply {
                        startPage = pageNumber
                        endPage = pageNumber
                    }
                    ExtractedPage(
                        pageNumber = pageNumber,
                        text = stripper.getText(document).trim()
                    )
                }
            }
        } catch (_: Exception) {
            // The caller converts parse failures into an explicit ingestion failure.
            emptyList()
        }
    }
}
