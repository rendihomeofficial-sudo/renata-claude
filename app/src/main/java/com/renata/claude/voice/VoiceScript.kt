package com.renata.claude.voice

/**
 * Turns a Markdown-ish assistant reply into something worth reading out loud.
 *
 * Pure Kotlin on purpose: the cleanup rules are the part most likely to annoy
 * users ("it says 'asterisk asterisk'"), so they must be unit testable.
 */
object VoiceScript {

    private val codeFence = Regex("```[\\s\\S]*?```")
    private val inlineCode = Regex("`([^`]*)`")
    private val heading = Regex("(?m)^\\s*#{1,6}\\s*")
    private val bullet = Regex("(?m)^\\s*[-*+]\\s+")
    private val numbered = Regex("(?m)^\\s*\\d+[.)]\\s+")
    private val blockQuote = Regex("(?m)^\\s*>\\s*")
    private val link = Regex("\\[([^\\]]*)]\\([^)]*\\)")
    private val emphasis = Regex("\\*{1,3}")
    private val horizontalRule = Regex("(?m)^\\s*([-*_])\\s*(\\1\\s*){2,}$")
    private val emoji = Regex(
        "[\\uD800-\\uDBFF][\\uDC00-\\uDFFF]" +      // surrogate pairs
            "|[\\u2190-\\u21FF\\u2600-\\u27BF\\uFE0F\\u2B00-\\u2BFF\\uD83D-\\uDDFF]"
    )
    private val spaces = Regex("[ \\t]+")
    private val blankLines = Regex("\\n{3,}")

    /** Abbreviations whose trailing dot does not end a sentence. */
    private val abbreviations = setOf(
        "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "vs", "etc",
        "e.g", "i.e", "approx", "fig", "no", "inc", "ltd", "co", "dept",
    )

    /**
     * Removes Markdown decoration so the text can be spoken plainly.
     *
     * Code fences are dropped entirely, inline code keeps its content, links
     * keep their label, list markers and headings are removed and emoji are
     * replaced by a space.
     */
    fun forSpeech(text: String): String {
        var out = text
        out = codeFence.replace(out, "\n")
        out = inlineCode.replace(out, "$1")
        out = link.replace(out, "$1")
        out = heading.replace(out, "")
        out = blockQuote.replace(out, "")
        out = bullet.replace(out, "")
        out = numbered.replace(out, "")
        out = horizontalRule.replace(out, "\n")
        out = emphasis.replace(out, "")
        out = emoji.replace(out, " ")
        out = out
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
        out = spaces.replace(out, " ")
        out = out.lines().joinToString("\n") { it.trim() }
        out = blankLines.replace(out, "\n\n")
        return out.trim()
    }

    /**
     * Splits a reply into sentences.
     *
     * The last item may be unfinished (no terminator yet) while the model is
     * still streaming, which is exactly what the speech cursor needs to know.
     */
    fun sentences(text: String): List<String> {
        val clean = forSpeech(text)
        if (clean.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        for (line in clean.split('\n')) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val buffer = StringBuilder()
            for (chunk in trimmed.split(Regex("(?<=[.!?…])\\s+"))) {
                if (chunk.isBlank()) continue
                if (buffer.isNotEmpty()) buffer.append(' ')
                buffer.append(chunk)
                val last = chunk.last()
                if ((last == '.' || last == '!' || last == '?' || last == '…') &&
                    !endsWithAbbreviation(chunk)
                ) {
                    result.add(buffer.toString().trim())
                    buffer.setLength(0)
                }
            }
            if (buffer.isNotEmpty()) {
                val tail = buffer.toString().trim()
                if (tail.isNotEmpty()) result.add(tail)
            }
        }
        return result.filter { it.isNotEmpty() }
    }

    /**
     * True when the sentence ends with a terminator, so it is a finished
     * sentence and not a fragment that is still being written.
     */
    fun isTerminated(sentence: String): Boolean {
        val trimmed = sentence.trim()
        if (trimmed.isEmpty()) return false
        val last = trimmed.last()
        return last == '.' || last == '!' || last == '?' || last == '…'
    }

    private fun endsWithAbbreviation(chunk: String): Boolean {
        val word = chunk.substringAfterLast(' ').lowercase().trimEnd('.')
        return word in abbreviations
    }
}

/**
 * Hands out sentences to speak while a reply is still streaming.
 *
 * Keeps a cursor of how many sentences were already spoken so the same
 * sentence is never spoken twice, and holds back the final sentence until it
 * is known to be complete.
 */
class SpeechCursor {

    private var spoken = 0

    /**
     * @param text the reply as it currently stands
     * @param flush true when the reply is complete and the trailing fragment
     *   (if any) may be spoken as well
     * @return the sentences that should be spoken now, in order
     */
    fun take(text: String, flush: Boolean): List<String> {
        val all = VoiceScript.sentences(text)
        if (all.size <= spoken) return emptyList()
        val fresh = all.subList(spoken, all.size)
        val out = mutableListOf<String>()
        fresh.forEachIndexed { index, sentence ->
            val isLast = index == fresh.lastIndex
            // A trailing fragment is held back until it is finished, so a
            // half written sentence is never read out loud. A sentence that
            // already has its terminator can be spoken right away.
            if (!isLast || flush || VoiceScript.isTerminated(sentence)) {
                out.add(sentence)
                spoken++
            }
        }
        return out
    }
}
