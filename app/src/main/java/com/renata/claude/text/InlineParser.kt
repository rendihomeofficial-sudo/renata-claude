package com.renata.claude.text

/**
 * One piece of an inline (inside a paragraph) text run.
 *
 * The inline parser produces plain data so it can be unit tested on the JVM
 * without Android or Compose classes; the UI layer turns these spans into an
 * `AnnotatedString`.
 */
data class InlineSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strike: Boolean = false,
    val link: String? = null,
    /** LaTeX source when the span is a math expression. */
    val math: String? = null,
)

/** Splits a paragraph into styled runs. */
object InlineParser {

    fun parse(source: String): List<InlineSpan> {
        val spans = mutableListOf<InlineSpan>()
        val buffer = StringBuilder()
        var i = 0

        fun flush() {
            if (buffer.isNotEmpty()) {
                spans.add(InlineSpan(buffer.toString()))
                buffer.setLength(0)
            }
        }

        while (i < source.length) {
            val c = source[i]
            when {
                c == '`' -> {
                    val end = source.indexOf('`', i + 1)
                    if (end > i) {
                        flush()
                        spans.add(InlineSpan(source.substring(i + 1, end), code = true))
                        i = end + 1
                    } else {
                        buffer.append(c)
                        i++
                    }
                }
                c == '$' -> {
                    val end = findMathEnd(source, i)
                    if (end > i) {
                        flush()
                        spans.add(InlineSpan(source.substring(i + 1, end), math = source.substring(i + 1, end)))
                        i = end + 1
                    } else {
                        buffer.append(c)
                        i++
                    }
                }
                c == '[' -> {
                    val link = parseLink(source, i)
                    if (link != null) {
                        flush()
                        spans.add(InlineSpan(link.first, link = link.second))
                        i = link.third
                    } else {
                        buffer.append(c)
                        i++
                    }
                }
                c == '~' && source.startsWith("~~", i) -> {
                    val end = source.indexOf("~~", i + 2)
                    if (end > i) {
                        flush()
                        spans.add(InlineSpan(source.substring(i + 2, end), strike = true))
                        i = end + 2
                    } else {
                        buffer.append(c)
                        i++
                    }
                }
                c == '*' || c == '_' -> {
                    val style = parseEmphasis(source, i, c)
                    if (style != null) {
                        flush()
                        spans.add(style.first)
                        i = style.second
                    } else {
                        buffer.append(c)
                        i++
                    }
                }
                else -> {
                    buffer.append(c)
                    i++
                }
            }
        }
        flush()
        return spans
    }

    /** Returns the index of the closing `$` of a math span, or -1. */
    private fun findMathEnd(source: String, start: Int): Int {
        var i = start + 1
        while (i < source.length) {
            when (source[i]) {
                '$' -> return if (i > start + 1) i else -1
                '\n' -> return -1
                else -> i++
            }
        }
        return -1
    }

    /** Parses `[label](url)` and returns label, url and the index after the link. */
    private fun parseLink(source: String, start: Int): Triple<String, String, Int>? {
        val labelEnd = source.indexOf(']', start + 1)
        if (labelEnd < 0) return null
        if (labelEnd + 1 >= source.length || source[labelEnd + 1] != '(') return null
        val urlEnd = source.indexOf(')', labelEnd + 2)
        if (urlEnd < 0) return null
        val label = source.substring(start + 1, labelEnd)
        val url = source.substring(labelEnd + 2, urlEnd).trim()
        return Triple(label, url, urlEnd + 1)
    }

    /**
     * Parses `**bold**`, `__bold__`, `*italic*` or `_italic_`.
     * Returns the span and the index after the closing marker.
     */
    private fun parseEmphasis(source: String, start: Int, marker: Char): Pair<InlineSpan, Int>? {
        val strong = source.startsWith("$marker$marker", start)
        val width = if (strong) 2 else 1
        val contentStart = start + width
        if (contentStart >= source.length) return null
        if (source[contentStart].isWhitespace()) return null

        val closing = if (strong) "$marker$marker" else "$marker"
        var i = contentStart
        while (i < source.length) {
            if (source[i] == '\\') {
                i += 2
                continue
            }
            if (source.startsWith(closing, i) && !source[i - 1].isWhitespace()) {
                val content = source.substring(contentStart, i)
                if (content.isEmpty()) return null
                val span = if (strong) {
                    InlineSpan(content, bold = true)
                } else {
                    InlineSpan(content, italic = true)
                }
                return span to (i + width)
            }
            i++
        }
        return null
    }
}
