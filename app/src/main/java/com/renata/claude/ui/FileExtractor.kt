package com.renata.claude.ui

import android.util.Base64
import java.io.ByteArrayInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Turns binary office documents into plain text so the model can read them.
 *
 * DOCX, XLSX and PPTX are ZIP containers with XML inside, so they can be
 * unpacked with the standard library only - no extra dependency and no
 * native code. PDF is not unpacked here: the raw bytes are sent to the API
 * as a native document block, which is how the Anthropic API expects PDFs.
 */
object FileExtractor {

    /** Returns the readable text of a document, or null when it cannot be read. */
    fun extractText(name: String, bytes: ByteArray): String? {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".docx") -> extractDocx(bytes)
            lower.endsWith(".xlsx") || lower.endsWith(".xlsm") -> extractXlsx(bytes)
            lower.endsWith(".pptx") -> extractPptx(bytes)
            lower.endsWith(".csv") || lower.endsWith(".tsv") -> decodeText(bytes)
            else -> null
        }
    }

    /** True when the file is a plain text file the model can read directly. */
    fun isPlainText(name: String, mime: String): Boolean {
        val lower = name.lowercase()
        if (mime.startsWith("text/")) return true
        if (mime == "application/json" || mime == "application/xml") return true
        if (mime == "application/javascript" || mime == "application/x-sh") return true
        return TEXT_EXTENSIONS.any { lower.endsWith(it) }
    }

    private val TEXT_EXTENSIONS = listOf(
        ".txt", ".md", ".markdown", ".csv", ".tsv", ".json", ".xml", ".html", ".htm",
        ".yml", ".yaml", ".toml", ".ini", ".cfg", ".conf", ".log", ".properties",
        ".java", ".kt", ".kts", ".py", ".js", ".jsx", ".ts", ".tsx", ".go", ".rs",
        ".c", ".h", ".cpp", ".hpp", ".cs", ".rb", ".php", ".swift", ".dart", ".sql",
        ".sh", ".bash", ".zsh", ".gradle", ".kt", ".env", ".gitignore", ".vue", ".svelte",
    )

    fun decodeText(bytes: ByteArray): String = String(bytes, Charsets.UTF_8)

    fun encodeBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun extractDocx(bytes: ByteArray): String? {
        val xml = readZipEntry(bytes, "word/document.xml") ?: return null
        val builder = StringBuilder()
        var index = 0
        while (index < xml.length) {
            val open = xml.indexOf("<w:p", index)
            if (open < 0) break
            val close = xml.indexOf("</w:p>", open)
            val end = if (close < 0) xml.length else close
            val paragraph = xml.substring(open, end)
            builder.append(paragraph.stripTags())
            builder.append('\n')
            index = if (close < 0) xml.length else close + 6
        }
        return builder.toString().trim().ifBlank { null }
    }

    private fun extractXlsx(bytes: ByteArray): String? {
        val shared = readSharedStrings(bytes)
        val sheetEntries = readZipEntries(bytes) { name ->
            name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")
        }.sortedBy { it.first }
        if (sheetEntries.isEmpty()) return null
        val builder = StringBuilder()
        sheetEntries.forEach { (name, xml) ->
            builder.append("=== Lembar ${name.substringAfterLast('/').removeSuffix(".xml")} ===\n")
            var index = 0
            while (true) {
                val open = xml.indexOf("<row", index)
                if (open < 0) break
                val close = xml.indexOf("</row>", open)
                val end = if (close < 0) xml.length else close
                val row = xml.substring(open, end)
                val cells = StringBuilder()
                var cellIndex = 0
                while (true) {
                    val cellOpen = row.indexOf("<c", cellIndex)
                    if (cellOpen < 0) break
                    val cellClose = row.indexOf(">", cellOpen)
                    if (cellClose < 0) break
                    val tag = row.substring(cellOpen, cellClose)
                    val isShared = tag.contains("t=\"s\"")
                    val valueOpen = row.indexOf("<v>", cellClose)
                    val valueClose = if (valueOpen < 0) -1 else row.indexOf("</v>", valueOpen)
                    val inlineOpen = row.indexOf("<is>", cellClose)
                    val inlineClose = if (inlineOpen < 0) -1 else row.indexOf("</is>", inlineOpen)
                    val cellText = when {
                        valueOpen >= 0 && valueClose > valueOpen -> {
                            val raw = row.substring(valueOpen + 3, valueClose)
                            if (isShared) {
                                raw.toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
                            } else {
                                raw
                            }
                        }
                        inlineOpen >= 0 && inlineClose > inlineOpen ->
                            row.substring(inlineOpen + 4, inlineClose).stripTags()
                        else -> ""
                    }
                    cells.append(cellText).append('\t')
                    cellIndex = cellClose + 1
                }
                builder.append(cells.toString().trimEnd('\t')).append('\n')
                index = if (close < 0) xml.length else close + 6
            }
        }
        return builder.toString().trim().ifBlank { null }
    }

    private fun readSharedStrings(bytes: ByteArray): List<String> {
        val xml = readZipEntry(bytes, "xl/sharedStrings.xml") ?: return emptyList()
        val out = ArrayList<String>()
        var index = 0
        while (true) {
            val open = xml.indexOf("<si", index)
            if (open < 0) break
            val close = xml.indexOf("</si>", open)
            val end = if (close < 0) xml.length else close
            out.add(xml.substring(open, end).stripTags())
            index = if (close < 0) xml.length else close + 5
        }
        return out
    }

    private fun extractPptx(bytes: ByteArray): String? {
        val slides = readZipEntries(bytes) { name ->
            name.startsWith("ppt/slides/slide") && name.endsWith(".xml")
        }.sortedBy { it.first }
        if (slides.isEmpty()) return null
        val builder = StringBuilder()
        slides.forEach { (name, xml) ->
            builder.append("=== Slide ${name.substringAfterLast('/').removeSuffix(".xml")} ===\n")
            var index = 0
            while (true) {
                val open = xml.indexOf("<a:t>", index)
                if (open < 0) break
                val close = xml.indexOf("</a:t>", open)
                if (close < 0) break
                builder.append(xml.substring(open + 5, close)).append('\n')
                index = close + 6
            }
            builder.append('\n')
        }
        return builder.toString().trim().ifBlank { null }
    }

    private fun readZipEntry(bytes: ByteArray, path: String): String? =
        readZipEntries(bytes) { it == path }.firstOrNull()?.second

    private fun readZipEntries(
        bytes: ByteArray,
        predicate: (String) -> Boolean,
    ): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory && predicate(name)) {
                        out.add(name to zip.readBytes().toString(Charsets.UTF_8))
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return out
    }

    /** Removes XML tags and decodes the common entities. */
    private fun String.stripTags(): String {
        val noTags = Regex("<[^>]*>").replace(this, "")
        return noTags
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .trim()
    }
}
