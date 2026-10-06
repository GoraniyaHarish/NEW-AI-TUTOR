package com.example.core.storage

import android.content.Context
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.InflaterInputStream

data class ExtractedPage(
    val pageNumber: Int,
    val text: String
)

data class ExtractedChunk(
    val pageNumber: Int,
    val chunkIndex: Int,
    val text: String
)

class PdfTextExtractor(private val context: Context) {

    fun extractTextFromUri(uri: Uri, fileName: String): List<ExtractedChunk> {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val inputStream = try {
            context.contentResolver.openInputStream(uri)
        } catch (_: Exception) {
            null
        }

        if (inputStream == null) {
            return listOf(
                ExtractedChunk(
                    pageNumber = 1,
                    chunkIndex = 0,
                    text = "[Unable to open file stream for '$fileName'. Please ensure the file exists and is accessible.]"
                )
            )
        }

        return when (extension) {
            "txt", "text", "md", "csv" -> {
                extractFromPlainText(inputStream)
            }
            "pdf" -> {
                extractFromPdf(inputStream, fileName)
            }
            else -> {
                extractFromPlainText(inputStream)
            }
        }
    }

    fun extractFromPlainText(inputStream: InputStream): List<ExtractedChunk> {
        val content = inputStream.bufferedReader().use { it.readText() }
        if (content.isBlank()) return emptyList()

        return chunkContent(listOf(ExtractedPage(1, content)))
    }

    fun extractFromPdf(inputStream: InputStream, fileName: String): List<ExtractedChunk> {
        val bytes = inputStream.use { it.readBytes() }
        val extractedPages = parsePdfBytes(bytes)

        if (extractedPages.isEmpty() || extractedPages.all { it.text.isBlank() }) {
            return listOf(
                ExtractedChunk(
                    pageNumber = 1,
                    chunkIndex = 0,
                    text = "[Could not extract readable text from '$fileName'. The document might be scanned, image-only, or encrypted.]"
                )
            )
        }

        return chunkContent(extractedPages)
    }

    /**
     * Lightweight pure-Kotlin PDF stream extractor:
     * Scans for FlateDecode streams, decompresses with InflaterInputStream,
     * and extracts string literals between ( and ) or hex strings.
     */
    private fun parsePdfBytes(bytes: ByteArray): List<ExtractedPage> {
        val pages = mutableListOf<ExtractedPage>()
        val pdfString = String(bytes, Charsets.ISO_8859_1)

        // Find stream markers
        val streamRegex = Regex("/Filter\\s*/FlateDecode[\\s\\S]*?stream\\r?\\n([\\s\\S]*?)\\r?\\nendstream")
        val streamMatches = streamRegex.findAll(pdfString)

        val fullTextBuilder = StringBuilder()

        for (match in streamMatches) {
            val streamStartIndex = match.groups[1]?.range?.first ?: continue
            val streamEndIndex = match.groups[1]?.range?.last ?: continue
            if (streamEndIndex <= streamStartIndex || streamEndIndex > bytes.size) continue

            try {
                val streamBytes = bytes.copyOfRange(streamStartIndex, streamEndIndex + 1)
                val decompressed = decompressFlate(streamBytes)
                val textFromStream = extractTextFromStream(decompressed)
                if (textFromStream.isNotBlank()) {
                    fullTextBuilder.append(textFromStream).append("\n\n")
                }
            } catch (_: Exception) {
                // If decompression fails for this stream, continue
            }
        }

        // Also check uncompressed text streams: BT ... ET
        val btRegex = Regex("BT([\\s\\S]*?)ET")
        for (match in btRegex.findAll(pdfString)) {
            val textContent = extractTextFromOperators(match.groupValues[1])
            if (textContent.isNotBlank()) {
                fullTextBuilder.append(textContent).append("\n")
            }
        }

        val extracted = fullTextBuilder.toString().trim()
        if (extracted.isNotBlank()) {
            val lines = extracted.lines()
            val linesPerPage = 35
            val chunks = lines.chunked(linesPerPage)
            chunks.forEachIndexed { index, pageLines ->
                val pageText = pageLines.joinToString("\n").trim()
                if (pageText.isNotBlank()) {
                    pages.add(ExtractedPage(pageNumber = index + 1, text = pageText))
                }
            }
        }

        return pages
    }

    private fun decompressFlate(input: ByteArray): String {
        return try {
            val bais = ByteArrayInputStream(input)
            val inflater = InflaterInputStream(bais)
            val baos = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            var len: Int
            while (inflater.read(buffer).also { len = it } > 0) {
                baos.write(buffer, 0, len)
            }
            baos.toString("UTF-8")
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractTextFromStream(content: String): String {
        val result = StringBuilder()
        val btRegex = Regex("BT([\\s\\S]*?)ET")
        val matches = btRegex.findAll(content)
        for (m in matches) {
            val clean = extractTextFromOperators(m.groupValues[1])
            if (clean.isNotBlank()) {
                result.append(clean).append("\n")
            }
        }
        return result.toString().trim()
    }

    private fun extractTextFromOperators(block: String): String {
        val sb = StringBuilder()
        val tjLiteralRegex = Regex("\\((.*?)\\)\\s*Tj")
        for (match in tjLiteralRegex.findAll(block)) {
            sb.append(match.groupValues[1]).append(" ")
        }
        val tjArrayRegex = Regex("\\[(.*?)\\]\\s*TJ")
        for (match in tjArrayRegex.findAll(block)) {
            val inside = match.groupValues[1]
            val subLiterals = Regex("\\((.*?)\\)").findAll(inside)
            for (sub in subLiterals) {
                sb.append(sub.groupValues[1]).append(" ")
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun chunkContent(pages: List<ExtractedPage>): List<ExtractedChunk> {
        val result = mutableListOf<ExtractedChunk>()
        var globalChunkIndex = 0

        for (page in pages) {
            val paragraphs = page.text.split(Regex("\n\n+"))
            var currentChunk = StringBuilder()

            for (p in paragraphs) {
                val cleanPara = p.trim()
                if (cleanPara.isEmpty()) continue

                if (currentChunk.length + cleanPara.length > 500 && currentChunk.isNotEmpty()) {
                    result.add(
                        ExtractedChunk(
                            pageNumber = page.pageNumber,
                            chunkIndex = globalChunkIndex++,
                            text = currentChunk.toString().trim()
                        )
                    )
                    currentChunk = StringBuilder()
                }

                currentChunk.append(cleanPara).append("\n\n")
            }

            if (currentChunk.isNotBlank()) {
                result.add(
                    ExtractedChunk(
                        pageNumber = page.pageNumber,
                        chunkIndex = globalChunkIndex++,
                        text = currentChunk.toString().trim()
                    )
                )
            }
        }

        return result
    }
}
