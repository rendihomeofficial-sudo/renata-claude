package com.renata.claude.text

/**
 * One block of a parsed markdown document.
 *
 * The parser is intentionally small and dependency free: it only covers the
 * subset of markdown that models actually emit in a chat answer, so the app
 * can render it without pulling in a third party library.
 */
sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class ListItem(val text: String, val ordered: Boolean, val marker: String, val indent: Int) : MdBlock()
    data class Code(val language: String?, val code: String) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    object Divider : MdBlock()
    data class Math(val latex: String, val display: Boolean) : MdBlock()
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock()
}

/** Parses markdown text into a flat list of blocks. */
object MarkdownParser {

    private val fenceRegex = Regex("^\\s*(`{3,}|~{3,})\\s*([A-Za-z0-9_+-]*)\\s*$")
    private val headingRegex = Regex("^\\s{0,3}(#{1,6})\\s+(.*)$")
    private val dividerRegex = Regex("^\\s{0,3}([-*_])\\s*(\\1\\s*){2,}$")
    private val quoteRegex = Regex("^\\s{0,3}>\\s?(.*)$")
    private val listRegex = Regex("^(\\s*)([-*+]|\\d+[.)])\\s+(.*)$")
    private val tableDividerRegex = Regex("^\\s*\\|?\\s*:?-{1,}:?\\s*(\\|\\s*:?-{1,}:?\\s*)*\\|?\\s*$")

    fun parse(source: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val lines = source.replace("\r\n", "\n").split('\n')
        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            val fence = fenceRegex.find(line)
            if (fence != null) {
                val language = fence.groupValues[2].takeIf { it.isNotBlank() }
                val body = StringBuilder()
                i++
                while (i < lines.size) {
                    val next = lines[i]
                    if (fenceRegex.matches(next)) {
                        i++
                        break
                    }
                    if (body.isNotEmpty()) body.append('\n')
                    body.append(next)
                    i++
                }
                blocks.add(MdBlock.Code(language, body.toString()))
                continue
            }

            val heading = headingRegex.find(line)
            if (heading != null) {
                blocks.add(MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim()))
                i++
                continue
            }

            if (dividerRegex.matches(line)) {
                blocks.add(MdBlock.Divider)
                i++
                continue
            }

            val quote = quoteRegex.find(line)
            if (quote != null) {
                val parts = mutableListOf<String>()
                while (i < lines.size) {
                    val m = quoteRegex.find(lines[i]) ?: break
                    parts.add(m.groupValues[1])
                    i++
                }
                blocks.add(MdBlock.Quote(parts.joinToString("\n").trim()))
                continue
            }

            if (line.trim() == "\$\$") {
                val body = StringBuilder()
                i++
                while (i < lines.size && lines[i].trim() != "\$\$") {
                    if (body.isNotEmpty()) body.append('\n')
                    body.append(lines[i])
                    i++
                }
                if (i < lines.size) i++ // closing $$
                blocks.add(MdBlock.Math(body.toString().trim(), display = true))
                continue
            }

            val list = listRegex.find(line)
            if (list != null) {
                val indent = list.groupValues[1].replace("\t", "  ").length / 2
                val marker = list.groupValues[2]
                val ordered = marker.first().isDigit()
                blocks.add(
                    MdBlock.ListItem(
                        text = list.groupValues[3].trim(),
                        ordered = ordered,
                        marker = marker,
                        indent = indent,
                    )
                )
                i++
                continue
            }

            if (line.contains('|') && i + 1 < lines.size && tableDividerRegex.matches(lines[i + 1])) {
                val header = splitTableRow(line)
                val rows = mutableListOf<List<String>>()
                i += 2
                while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) {
                    rows.add(splitTableRow(lines[i]))
                    i++
                }
                blocks.add(MdBlock.Table(header, rows))
                continue
            }

            if (line.isBlank()) {
                i++
                continue
            }

            val paragraph = StringBuilder()
            while (i < lines.size) {
                val current = lines[i]
                if (current.isBlank()) break
                if (fenceRegex.matches(current) ||
                    headingRegex.matches(current) ||
                    dividerRegex.matches(current) ||
                    quoteRegex.matches(current) ||
                    listRegex.matches(current)
                ) break
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(current.trim())
                i++
            }
            blocks.add(MdBlock.Paragraph(paragraph.toString()))
        }
        return blocks
    }

    private fun splitTableRow(line: String): List<String> {
        val trimmed = line.trim().removePrefix("|").removeSuffix("|")
        return trimmed.split('|').map { it.trim() }
    }
}
