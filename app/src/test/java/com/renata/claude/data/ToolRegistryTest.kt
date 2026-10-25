package com.renata.claude.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The local tools run on the device, so their behaviour can be checked without
 * a network or a model in the loop.
 */
class ToolRegistryTest {

    @Test
    fun specsCoverEveryLocalTool() {
        val specs = ToolRegistry.specs()
        val names = specs.map { it.name }
        assertTrue(names.contains(ToolRegistry.NAME_DATETIME))
        assertTrue(names.contains(ToolRegistry.NAME_CALCULATE))
        assertTrue(names.contains(ToolRegistry.NAME_FETCH_URL))
        specs.forEach { spec ->
            assertEquals("object", spec.inputSchema.getString("type"))
            assertFalse(spec.description.isBlank())
        }
    }

    @Test
    fun calculateReturnsTheValueAndTheExpression() = runBlocking {
        val result = ToolRegistry.execute(
            ToolCall(
                id = "call_1",
                name = ToolRegistry.NAME_CALCULATE,
                arguments = """{"expression":"(12 + 8) * 3.5"}""",
            )
        )
        assertFalse(result.isError)
        assertEquals("70 = (12 + 8) * 3.5", result.content)
        assertEquals("call_1", result.toolCallId)
    }

    @Test
    fun calculateUnderstandsFunctionsAndPowers() = runBlocking {
        val result = ToolRegistry.execute(
            ToolCall(
                id = "call_2",
                name = ToolRegistry.NAME_CALCULATE,
                arguments = """{"expression":"sqrt(81) + 2^3"}""",
            )
        )
        assertFalse(result.isError)
        assertTrue(result.content.startsWith("17 = "))
    }

    @Test
    fun calculateReportsBrokenExpressionsAsErrors() = runBlocking {
        val result = ToolRegistry.execute(
            ToolCall(
                id = "call_3",
                name = ToolRegistry.NAME_CALCULATE,
                arguments = """{"expression":"1 / 0"}""",
            )
        )
        assertTrue(result.isError)
        assertTrue(result.content.contains("tidak bisa dihitung"))
    }

    @Test
    fun calculateRejectsAnEmptyExpression() = runBlocking {
        val result = ToolRegistry.execute(
            ToolCall(id = "call_4", name = ToolRegistry.NAME_CALCULATE, arguments = "{}")
        )
        assertTrue(result.isError)
        assertTrue(result.content.contains("kosong"))
    }

    @Test
    fun unknownToolsAreReportedAsErrors() = runBlocking {
        val result = ToolRegistry.execute(ToolCall(id = "call_5", name = "hapus_semua"))
        assertTrue(result.isError)
        assertTrue(result.content.contains("Alat tidak dikenal"))
    }

    @Test
    fun fetchUrlRefusesUrlsWithoutAScheme() = runBlocking {
        val result = ToolRegistry.execute(
            ToolCall(
                id = "call_6",
                name = ToolRegistry.NAME_FETCH_URL,
                arguments = """{"url":"example.com"}""",
            )
        )
        assertTrue(result.isError)
        assertTrue(result.content.contains("http://"))
    }

    @Test
    fun dateTimeMentionsTheRequestedTimeZone() {
        val text = ToolRegistry.currentDateTime("Asia/Jakarta")
        assertTrue(text.contains("Asia/Jakarta"))
        assertTrue(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}""").containsMatchIn(text))
    }

    @Test
    fun htmlToTextDropsScriptsAndTags() {
        val html = """
            <html><head><style>bodi{color:red}</style><script>alert('x')</script></head>
            <body><h1>Judul</h1><p>Paragraf&nbsp;pertama</p><p>Paragraf kedua</p></body></html>
        """.trimIndent()
        val text = ToolRegistry.htmlToText(html)
        assertFalse(text.contains("alert"))
        assertFalse(text.contains("color:red"))
        assertFalse(text.contains("<"))
        assertTrue(text.contains("Judul"))
        assertTrue(text.contains("Paragraf pertama"))
        assertTrue(text.contains("Paragraf kedua"))
    }

    @Test
    fun formatNumberDropsUselessZeros() {
        assertEquals("70", ToolRegistry.formatNumber(70.0))
        assertEquals("3.5", ToolRegistry.formatNumber(3.5))
        assertEquals("0", ToolRegistry.formatNumber(0.0))
        assertEquals("NaN", ToolRegistry.formatNumber(Double.NaN))
    }

    @Test
    fun toolCallReadsItsArguments() {
        val call = ToolCall(id = "x", name = "calculate", arguments = """{"expression":"1+1"}""")
        assertEquals("1+1", call.argument("expression"))
        assertEquals("", call.argument("missing"))
    }
}
