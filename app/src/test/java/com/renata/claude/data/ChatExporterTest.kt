package com.renata.claude.data

import org.junit.Assert.assertTrue
import org.junit.Test

class ChatExporterTest {

    private val sampleChat = Chat(
        title = "Diskusi Kotlin",
        messages = listOf(
            Message(
                role = "user",
                text = "Bagaimana cara membuat data class di Kotlin?",
            ),
            Message(
                role = "assistant",
                model = "claude-3-5-sonnet-latest",
                thinking = "Menjelaskan sintaks data class.",
                text = "Cukup gunakan kata kunci `data class Person(val name: String)`.",
                citations = listOf(
                    Citation(title = "Kotlin Lang Docs", url = "https://kotlinlang.org")
                )
            )
        )
    )

    @Test
    fun `ekspor markdown menyertakan judul peran dan isi`() {
        val md = ChatExporter.toMarkdown(sampleChat)
        assertTrue(md.contains("# Diskusi Kotlin"))
        assertTrue(md.contains("### 👤 Anda"))
        assertTrue(md.contains("Bagaimana cara membuat data class di Kotlin?"))
        assertTrue(md.contains("### 🤖 Renata Claude *(claude-3-5-sonnet-latest)*"))
        assertTrue(md.contains("> **[Proses Berpikir]**"))
        assertTrue(md.contains("data class Person(val name: String)"))
        assertTrue(md.contains("[Kotlin Lang Docs](https://kotlinlang.org)"))
    }

    @Test
    fun `ekspor teks polos menyertakan format teks rapi`() {
        val txt = ChatExporter.toPlainText(sampleChat)
        assertTrue(txt.contains("=== Diskusi Kotlin ==="))
        assertTrue(txt.contains("Anda:"))
        assertTrue(txt.contains("Renata Claude:"))
        assertTrue(txt.contains("https://kotlinlang.org"))
    }
}
