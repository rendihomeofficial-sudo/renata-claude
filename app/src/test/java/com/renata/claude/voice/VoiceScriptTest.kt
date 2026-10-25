package com.renata.claude.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceScriptTest {

    @Test
    fun forSpeechRemovesMarkdownDecoration() {
        assertEquals(
            "Here is the plan: ship it",
            VoiceScript.forSpeech("**Here is the plan:** ship it"),
        )
    }

    @Test
    fun forSpeechDropsCodeFences() {
        val cleaned = VoiceScript.forSpeech("Try this:\n```kotlin\nval x = 1\n```\nDone.")
        assertTrue(cleaned.contains("Done."))
        assertTrue(!cleaned.contains("val x"))
    }

    @Test
    fun forSpeechKeepsLinkLabel() {
        assertEquals(
            "see the docs",
            VoiceScript.forSpeech("see the [docs](https://example.com)"),
        )
    }

    @Test
    fun sentencesSplitOnTerminators() {
        assertEquals(
            listOf("Hello there.", "How are you?"),
            VoiceScript.sentences("Hello there. How are you?"),
        )
    }

    @Test
    fun sentencesKeepTrailingFragmentWhileStreaming() {
        val sentences = VoiceScript.sentences("Hello there. Still typ")
        assertEquals(listOf("Hello there.", "Still typ"), sentences)
    }

    @Test
    fun sentencesDoNotSplitAfterAbbreviations() {
        assertEquals(
            listOf("Ask Mr. Adi about it."),
            VoiceScript.sentences("Ask Mr. Adi about it."),
        )
    }

    @Test
    fun cursorSpeaksEachSentenceOnlyOnce() {
        val cursor = SpeechCursor()
        assertEquals(listOf("Hello there."), cursor.take("Hello there. How are", flush = false))
        assertEquals(listOf("How are you?"), cursor.take("Hello there. How are you?", flush = false))
        assertEquals(emptyList<String>(), cursor.take("Hello there. How are you?", flush = false))
    }

    @Test
    fun cursorHoldsBackTheLastFragmentUntilFlush() {
        val cursor = SpeechCursor()
        assertEquals(listOf("Hello there."), cursor.take("Hello there. Still", flush = false))
        assertEquals(listOf("Still talking"), cursor.take("Hello there. Still talking", flush = true))
    }

    @Test
    fun cursorSpeaksATrailingSentenceAsSoonAsItIsFinished() {
        val cursor = SpeechCursor()
        assertEquals(listOf("How are you?"), cursor.take("How are you?", flush = false))
        assertEquals(emptyList<String>(), cursor.take("How are you?", flush = false))
    }

    @Test
    fun cursorFlushSpeaksTheTrailingFragment() {
        val cursor = SpeechCursor()
        assertEquals(emptyList<String>(), cursor.take("No terminator here", flush = false))
        assertEquals(listOf("No terminator here"), cursor.take("No terminator here", flush = true))
    }
}
