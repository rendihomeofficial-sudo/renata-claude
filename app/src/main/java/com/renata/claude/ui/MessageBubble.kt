package com.renata.claude.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.renata.claude.data.Attachment
import com.renata.claude.data.Message
import com.renata.claude.data.ToolCall
import com.renata.claude.data.ToolResult

@Composable
fun MessageBubble(
    message: Message,
    isStreaming: Boolean,
    onCopy: (String) -> Unit,
    onShare: ((String) -> Unit)? = null,
    onRetry: (() -> Unit)?,
    onOpenCode: ((language: String?, code: String) -> Unit)? = null,
    onOpenLink: ((String) -> Unit)? = null,
) {
    val isUser = message.role == "user"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
            contentColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(18.dp),
            tonalElevation = if (isUser) 0.dp else 1.dp,
            modifier = Modifier.widthIn(max = 560.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (!isUser && message.thinking.isNotBlank()) {
                    ThinkingBlock(message.thinking)
                    Spacer(Modifier.height(6.dp))
                }
                if (message.attachments.isNotEmpty()) {
                    message.attachments.forEach { AttachmentView(it) }
                    if (message.text.isNotBlank()) Spacer(Modifier.height(6.dp))
                }
                if (message.toolResults.isNotEmpty()) {
                    message.toolResults.forEach { ToolResultView(it) }
                    if (message.text.isNotBlank()) Spacer(Modifier.height(6.dp))
                }
                val body = message.text
                if (body.isNotBlank()) {
                    if (isUser) {
                        Text(text = body, style = MaterialTheme.typography.bodyLarge)
                    } else {
                        MarkdownText(text = body, onOpenCode = onOpenCode, onOpenLink = onOpenLink)
                    }
                } else if (isStreaming && !isUser) {
                    Text(text = "…", style = MaterialTheme.typography.bodyLarge)
                }
                if (!isUser && message.toolCalls.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    message.toolCalls.forEach { ToolCallView(it) }
                }
                if (!isUser && message.citations.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    CitationList(message.citations, onOpenLink)
                }
                if (message.failed) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Permintaan gagal. Periksa kunci API, Base URL, dan model di Pengaturan.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        if (!isUser) {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onCopy(message.text) }, enabled = message.text.isNotBlank()) {
                    Text("Salin")
                }
                if (onShare != null) {
                    TextButton(onClick = { onShare(message.text) }, enabled = message.text.isNotBlank()) {
                        Text("Bagikan")
                    }
                }
                if (onRetry != null) {
                    TextButton(onClick = onRetry, enabled = !isStreaming) {
                        Text("Ulangi")
                    }
                }
                message.model?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolCallView(call: ToolCall) {
    var expanded by remember(call.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp),
    ) {
        TextButton(
            onClick = { expanded = !expanded },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            Text(
                text = if (expanded) "▼ Alat: ${call.name}" else "▶ Alat: ${call.name}",
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (expanded) {
            Text(
                text = call.prettyArguments(),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToolResultView(result: ToolResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp),
    ) {
        Text(
            text = "Hasil ${result.name}" + if (result.isError) " (gagal)" else "",
            style = MaterialTheme.typography.labelMedium,
            color = if (result.isError) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = result.content.take(600),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThinkingBlock(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp),
    ) {
        TextButton(onClick = { expanded = !expanded }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text(if (expanded) "▼ Berpikir" else "▶ Berpikir")
        }
        if (expanded) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AttachmentView(att: Attachment) {
    when (att.kind) {
        "image" -> {
            val bitmap by produceState<ImageBitmap?>(null, att.id) {
                value = att.base64?.let { b64 ->
                    runCatching {
                        val bytes = Base64.decode(b64, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }.getOrNull()
                }
            }
            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = att.name,
                    modifier = Modifier
                        .sizeIn(maxWidth = 220.dp, maxHeight = 260.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            } else {
                Text("🖼 ${att.name}")
            }
        }
        "document" -> Text("📄 ${att.name}")
        else -> Text("📎 ${att.name}")
    }
}
