package com.renata.claude.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceInputTest {

    @Test
    fun mergeKeepsTypedTextWhenTranscriptIsBlank() {
        assertEquals("hello", VoiceInput.merge("hello", "   "))
    }

    @Test
    fun mergeUsesTranscriptWhenNothingTyped() {
        assertEquals("hello world", VoiceInput.merge("", "  hello world  "))
    }

    @Test
    fun mergeJoinsTypedTextAndTranscriptWithSingleSpace() {
        assertEquals(
            "hello how are you",
            VoiceInput.merge("hello", "how  are\nyou"),
        )
    }

    @Test
    fun mergeNormalisesWhitespaceInTranscript() {
        assertEquals("a b c", VoiceInput.merge("", "  a   b \n c  "))
    }

    @Test
    fun pickBestReturnsFirstNonBlankCandidate() {
        assertEquals("second", VoiceInput.pickBest(listOf("", "  ", "second", "other")))
    }

    @Test
    fun pickBestReturnsNullWhenNoCandidateHasText() {
        assertNull(VoiceInput.pickBest(listOf("", "   ")))
        assertNull(VoiceInput.pickBest(null))
    }

    @Test
    fun isSendableOnlyAcceptsNonBlankText() {
        assertTrue(VoiceInput.isSendable("hi"))
        assertFalse(VoiceInput.isSendable("   "))
        assertFalse(VoiceInput.isSendable(""))
    }
}
