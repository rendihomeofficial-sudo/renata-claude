package com.renata.claude.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp,
            ),
            tonalElevation = if (isUser) 0.dp else 0.5.dp,
            modifier = Modifier.widthIn(max = 580.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (!isUser && message.thinking.isNotBlank()) {
                    ThinkingBlock(message.thinking)
                    Spacer(Modifier.height(8.dp))
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
                        Column {
                            MarkdownText(text = body, onOpenCode = onOpenCode, onOpenLink = onOpenLink)
                            if (isStreaming) {
                                Spacer(Modifier.height(4.dp))
                                ClaudeStreamingCursor()
                            }
                        }
                    }
                } else if (isStreaming && !isUser) {
                    ClaudeThinkingIndicator()
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
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ClaudeMiniSparkle(color = MaterialTheme.colorScheme.primary)
                Text(
                    text = "Proses berpikir",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Tutup pemikiran" else "Buka pemikiran",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}

/** Indikator loading khas Claude: Bintang 4-sudut bernapas (*pulsing starburst*) */
@Composable
fun ClaudeThinkingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "claudeThinking")
    val pulse by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )
    val primary = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .scale(pulse),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(20.dp)) {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(w * 0.5f, 0f)
                    cubicTo(w * 0.52f, h * 0.38f, w * 0.62f, h * 0.48f, w, h * 0.5f)
                    cubicTo(w * 0.62f, h * 0.52f, w * 0.52f, h * 0.62f, w * 0.5f, h)
                    cubicTo(w * 0.48f, h * 0.62f, w * 0.38f, h * 0.52f, 0f, h * 0.5f)
                    cubicTo(w * 0.38f, h * 0.48f, w * 0.48f, h * 0.38f, w * 0.5f, 0f)
                    close()
                }
                drawPath(path = path, color = primary.copy(alpha = alpha))
            }
        }
        Text(
            text = "Claude sedang berpikir…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Mini Sparkle icon untuk accordion proses berpikir. */
@Composable
private fun ClaudeMiniSparkle(color: Color) {
    Canvas(modifier = Modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, 0f)
            cubicTo(w * 0.53f, h * 0.38f, w * 0.62f, h * 0.47f, w, h * 0.5f)
            cubicTo(w * 0.62f, h * 0.53f, w * 0.53f, h * 0.62f, w * 0.5f, h)
            cubicTo(w * 0.47f, h * 0.62f, w * 0.38f, h * 0.53f, 0f, h * 0.5f)
            cubicTo(w * 0.38f, h * 0.47f, w * 0.47f, h * 0.38f, w * 0.5f, 0f)
            close()
        }
        drawPath(path = path, color = color)
    }
}

/** Kursor blinking terracotta khas Claude saat streaming teks. */
@Composable
private fun ClaudeStreamingCursor() {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(550),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursorAlpha",
    )
    Box(
        modifier = Modifier
            .size(width = 8.dp, height = 15.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha), RoundedCornerShape(2.dp)),
    )
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
