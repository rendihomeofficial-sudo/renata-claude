package com.renata.claude.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.renata.claude.data.Attachment

/**
 * Turns any picked file into an [Attachment] the API understands.
 *
 * - images stay binary and are sent as image blocks
 * - PDF stays binary and is sent as a native document block
 * - DOCX / XLSX / PPTX are unpacked locally into text
 * - everything else that looks like text is decoded as UTF-8
 */
object AttachmentFactory {

    const val MAX_BYTES = 20L * 1024 * 1024

    fun fromUri(context: Context, uri: Uri): Attachment? {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: guessMime(uri.toString())
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "berkas"
        var size = 0L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0) cursor.getString(nameIdx)?.let { name = it }
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                    }
                }
        }

        val bytes = runCatching {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return null

        if (bytes.size > MAX_BYTES) return null
        if (size == 0L) size = bytes.size.toLong()

        val lowerName = name.lowercase()

        // Office documents and plain text are converted to text so the model
        // can actually read the content.
        val extracted = FileExtractor.extractText(name, bytes)
        if (extracted != null) {
            return Attachment(
                name = name,
                mediaType = "text/plain",
                kind = "text",
                text = extracted,
                sizeBytes = size,
            )
        }

        if (FileExtractor.isPlainText(name, mime)) {
            return Attachment(
                name = name,
                mediaType = "text/plain",
                kind = "text",
                text = FileExtractor.decodeText(bytes),
                sizeBytes = size,
            )
        }

        return Attachment(
            name = name,
            mediaType = mime,
            kind = if (mime.startsWith("image/")) "image" else "document",
            base64 = FileExtractor.encodeBase64(bytes),
            sizeBytes = size,
        )
    }

    private fun guessMime(path: String): String {
        val lower = path.lowercase()
        return when {
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".bmp") -> "image/bmp"
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".txt") || lower.endsWith(".md") -> "text/plain"
            lower.endsWith(".docx") ->
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            lower.endsWith(".xlsx") ->
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            lower.endsWith(".pptx") ->
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            else -> "application/octet-stream"
        }
    }
}
