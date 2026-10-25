package com.renata.claude.voice

/**
 * Pure, framework-free helpers for the voice input feature.
 *
 * Kept free of Android imports on purpose so the merging rules can be covered
 * by plain JVM unit tests.
 */
object VoiceInput {

    /**
     * Merges a spoken transcript into whatever the user already typed.
     *
     * Rules:
     * - blank transcript -> existing input is returned untouched
     * - existing input blank -> transcript is trimmed and used as-is
     * - both present -> joined with a single space, whitespace normalised
     */
    fun merge(existingInput: String, transcript: String): String {
        val spoken = transcript.trim().replace(Regex("\\s+"), " ")
        if (spoken.isEmpty()) return existingInput
        val typed = existingInput.trim()
        if (typed.isEmpty()) return spoken
        return "$typed $spoken"
    }

    /**
     * Picks the best candidate from the list of recognition results.
     *
     * Recognizers return alternatives ordered by confidence; the first
     * non-blank one wins. Blank entries are skipped.
     */
    fun pickBest(candidates: List<String>?): String? =
        candidates?.firstOrNull { it.isNotBlank() }?.trim()

    /**
     * A transcript is only worth sending when it actually carries text.
     */
    fun isSendable(text: String): Boolean = text.isNotBlank()
}
