package com.renata.claude.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineParserTest {

    @Test
    fun `parses bold and italic`() {
        val spans = InlineParser.parse("**tebal** dan *miring*")
        assertEquals("tebal", spans[0].text)
        assertTrue(spans[0].bold)
        assertEquals(" dan ", spans[1].text)
        assertEquals("miring", spans[2].text)
        assertTrue(spans[2].italic)
    }

    @Test
    fun `parses inline code`() {
        val spans = InlineParser.parse("pakai `val x = 1` di sini")
        assertEquals("val x = 1", spans[1].text)
        assertTrue(spans[1].code)
    }

    @Test
    fun `parses link`() {
        val spans = InlineParser.parse("lihat [dokumen](https://contoh.id)")
        val link = spans.first { it.link != null }
        assertEquals("dokumen", link.text)
        assertEquals("https://contoh.id", link.link)
    }

    @Test
    fun `parses strike through`() {
        val spans = InlineParser.parse("~~batal~~")
        assertTrue(spans[0].strike)
        assertEquals("batal", spans[0].text)
    }

    @Test
    fun `parses inline math`() {
        val spans = InlineParser.parse("nilai \$x^2\$ di sini")
        val math = spans.first { it.math != null }
        assertEquals("x^2", math.math)
    }

    @Test
    fun `unmatched markers stay literal`() {
        val spans = InlineParser.parse("2 * 3 = 6")
        assertEquals(1, spans.size)
        assertEquals("2 * 3 = 6", spans[0].text)
        assertFalse(spans[0].bold)
        assertNotNull(spans[0].text)
    }
}
