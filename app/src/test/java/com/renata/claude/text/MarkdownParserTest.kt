package com.renata.claude.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun `parses heading and paragraph`() {
        val blocks = MarkdownParser.parse("# Judul\n\nHalo dunia")
        assertEquals(2, blocks.size)
        val heading = blocks[0] as MdBlock.Heading
        assertEquals(1, heading.level)
        assertEquals("Judul", heading.text)
        assertEquals("Halo dunia", (blocks[1] as MdBlock.Paragraph).text)
    }

    @Test
    fun `parses fenced code with language`() {
        val blocks = MarkdownParser.parse("```kotlin\nval x = 1\n```")
        assertEquals(1, blocks.size)
        val code = blocks[0] as MdBlock.Code
        assertEquals("kotlin", code.language)
        assertEquals("val x = 1", code.code)
    }

    @Test
    fun `code block keeps internal blank lines`() {
        val blocks = MarkdownParser.parse("```\na\n\nb\n```")
        val code = blocks[0] as MdBlock.Code
        assertEquals("a\n\nb", code.code)
    }

    @Test
    fun `parses unordered and ordered list items`() {
        val blocks = MarkdownParser.parse("- satu\n- dua\n\n1. pertama")
        val items = blocks.filterIsInstance<MdBlock.ListItem>()
        assertEquals(3, items.size)
        assertTrue(!items[0].ordered)
        assertEquals("satu", items[0].text)
        assertTrue(items[2].ordered)
        assertEquals("1.", items[2].marker)
    }

    @Test
    fun `parses blockquote`() {
        val blocks = MarkdownParser.parse("> kutipan satu\n> kutipan dua")
        val quote = blocks[0] as MdBlock.Quote
        assertEquals("kutipan satu\nkutipan dua", quote.text)
    }

    @Test
    fun `parses display math block`() {
        val blocks = MarkdownParser.parse("$$\nE = mc^2\n$$")
        val math = blocks[0] as MdBlock.Math
        assertTrue(math.display)
        assertEquals("E = mc^2", math.latex)
    }

    @Test
    fun `parses table with header and rows`() {
        val blocks = MarkdownParser.parse("| Nama | Nilai |\n| --- | --- |\n| A | 1 |")
        val table = blocks[0] as MdBlock.Table
        assertEquals(listOf("Nama", "Nilai"), table.header)
        assertEquals(1, table.rows.size)
        assertEquals(listOf("A", "1"), table.rows[0])
    }

    @Test
    fun `parses divider`() {
        val blocks = MarkdownParser.parse("---")
        assertTrue(blocks[0] is MdBlock.Divider)
    }
}
