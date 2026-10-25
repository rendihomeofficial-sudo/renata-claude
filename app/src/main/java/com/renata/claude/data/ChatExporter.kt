package com.renata.claude.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Memformat percakapan [Chat] menjadi berkas teks atau Markdown untuk ekspor. */
object ChatExporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    /**
     * Mengubah [chat] menjadi format Markdown yang rapi dan mudah dibaca.
     */
    fun toMarkdown(chat: Chat): String = buildString {
        appendLine("# ${chat.title}")
        appendLine()
        appendLine("*Dibuat: ${formatTime(chat.createdAt)} | Diperbarui: ${formatTime(chat.updatedAt)}*")
        appendLine()
        appendLine("---")
        appendLine()

        chat.messages.forEach { message ->
            val roleName = if (message.role == "user") "### 👤 Anda" else "### 🤖 Renata Claude"
            val modelInfo = message.model?.let { " *($it)*" }.orEmpty()
            appendLine("$roleName$modelInfo")
            appendLine()

            if (message.thinking.isNotBlank()) {
                appendLine("> **[Proses Berpikir]**")
                message.thinking.lines().forEach { line ->
                    appendLine("> $line")
                }
                appendLine()
            }

            if (message.attachments.isNotEmpty()) {
                appendLine("**Lampiran:**")
                message.attachments.forEach { att ->
                    appendLine("- [${att.kind}] ${att.name}")
                }
                appendLine()
            }

            if (message.text.isNotBlank()) {
                appendLine(message.text)
                appendLine()
            }

            if (message.citations.isNotEmpty()) {
                appendLine("**Sumber Referensi:**")
                message.citations.forEachIndexed { i, c ->
                    appendLine("${i + 1}. [${c.title}](${c.url})")
                }
                appendLine()
            }

            appendLine("---")
            appendLine()
        }
    }

    /**
     * Mengubah [chat] menjadi format teks polos (plain text).
     */
    fun toPlainText(chat: Chat): String = buildString {
        appendLine("=== ${chat.title} ===")
        appendLine("Waktu: ${formatTime(chat.createdAt)}")
        appendLine("==========================================")
        appendLine()

        chat.messages.forEach { message ->
            val sender = if (message.role == "user") "Anda" else "Renata Claude"
            val time = formatTime(message.createdAt)
            appendLine("[$time] $sender:")
            if (message.attachments.isNotEmpty()) {
                appendLine("Lampiran: " + message.attachments.joinToString(", ") { it.name })
            }
            if (message.text.isNotBlank()) {
                appendLine(message.text)
            }
            if (message.citations.isNotEmpty()) {
                appendLine("Sumber: " + message.citations.joinToString("; ") { "${it.title} (${it.url})" })
            }
            appendLine()
        }
    }

    private fun formatTime(millis: Long): String = try {
        dateFormat.format(Date(millis))
    } catch (_: Exception) {
        ""
    }
}
