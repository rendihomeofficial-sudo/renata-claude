package com.renata.claude.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.renata.claude.text.InlineParser
import com.renata.claude.text.InlineSpan
import com.renata.claude.text.Latex
import com.renata.claude.text.MdBlock
import com.renata.claude.text.MarkdownParser

/**
 * Renders a markdown answer.
 *
 * The parser lives in the `text` package so it can be unit tested on the
 * JVM; this composable only turns the parsed blocks into Compose widgets.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    onOpenCode: ((language: String?, code: String) -> Unit)? = null,
    onOpenLink: ((String) -> Unit)? = null,
) {
    val blocks = remember(text) { MarkdownParser.parse(text) }
    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) Spacer(Modifier.height(6.dp))
            MarkdownBlock(block, onOpenCode, onOpenLink)
        }
    }
}

@Composable
private fun MarkdownBlock(
    block: MdBlock,
    onOpenCode: ((language: String?, code: String) -> Unit)?,
    onOpenLink: ((String) -> Unit)?,
) {
    when (block) {
        is MdBlock.Heading -> {
            val style = when (block.level) {
                1 -> MaterialTheme.typography.titleLarge
                2 -> MaterialTheme.typography.titleMedium
                3 -> MaterialTheme.typography.titleSmall
                else -> MaterialTheme.typography.bodyLarge
            }
            InlineText(
                text = block.text,
                style = style.copy(fontWeight = FontWeight.Bold),
                onOpenLink = onOpenLink,
            )
        }

        is MdBlock.Paragraph -> InlineText(
            text = block.text,
            style = MaterialTheme.typography.bodyLarge,
            onOpenLink = onOpenLink,
        )

        is MdBlock.ListItem -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (block.indent * 14).dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = if (block.ordered) "${block.marker}" else "•",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.width(if (block.ordered) 26.dp else 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            InlineText(
                text = block.text,
                style = MaterialTheme.typography.bodyLarge,
                onOpenLink = onOpenLink,
                modifier = Modifier.weight(1f),
            )
        }

        is MdBlock.Code -> CodeBlock(
            language = block.language,
            code = block.code,
            onOpen = onOpenCode,
        )

        is MdBlock.Quote -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(20.dp)
                    .background(MaterialTheme.colorScheme.outline),
            )
            Spacer(Modifier.width(8.dp))
            InlineText(
                text = block.text,
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                onOpenLink = onOpenLink,
            )
        }

        MdBlock.Divider -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        is MdBlock.Math -> Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = Latex.toUnicode(block.latex),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(10.dp),
            )
        }

        is MdBlock.Table -> MarkdownTable(block, onOpenLink)
    }
}

@Composable
private fun MarkdownTable(
    table: MdBlock.Table,
    onOpenLink: ((String) -> Unit)?,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
    ) {
        Row(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
            table.header.forEach { cell ->
                InlineText(
                    text = cell,
                    style = MaterialTheme.typography.labelLarge,
                    onOpenLink = onOpenLink,
                    modifier = Modifier
                        .width(140.dp)
                        .padding(8.dp),
                )
            }
        }
        table.rows.forEach { row ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row {
                row.forEach { cell ->
                    InlineText(
                        text = cell,
                        style = MaterialTheme.typography.bodySmall,
                        onOpenLink = onOpenLink,
                        modifier = Modifier
                            .width(140.dp)
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

/**
 * Renders inline markdown text.
 *
 * When [onOpenLink] is given, a link can be tapped and the URL is handed
 * over so the app can open it in the browser.
 */
@Composable
private fun InlineText(
    text: String,
    style: TextStyle,
    onOpenLink: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val annotated = remember(text) { inlineAnnotatedString(text) }
    if (onOpenLink == null) {
        Text(text = annotated, style = style, modifier = modifier)
        return
    }
    @Suppress("DEPRECATION")
    ClickableText(
        text = annotated,
        style = style,
        modifier = modifier.fillMaxWidth(),
        onClick = { offset ->
            annotated.getStringAnnotations(LINK_TAG, offset, offset)
                .firstOrNull()
                ?.let { onOpenLink(it.item) }
        },
    )
}

@Composable
private fun CodeBlock(
    language: String?,
    code: String,
    onOpen: ((language: String?, code: String) -> Unit)?,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = language?.takeIf { it.isNotBlank() } ?: "kode",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (onOpen != null) {
                    TextButton(onClick = { onOpen(language, code) }) {
                        Text("Buka", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            )
        }
    }
}

/** Turns a markdown run into styled text. */
private fun inlineAnnotatedString(source: String): AnnotatedString = buildAnnotatedString {
    InlineParser.parse(source).forEach { span ->
        appendSpan(span)
    }
}

private const val LINK_TAG = "url"

private fun AnnotatedString.Builder.appendSpan(span: InlineSpan) {
    if (span.math != null) {
        withStyle(
            SpanStyle(
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
            )
        ) {
            append(Latex.toUnicode(span.math))
        }
        return
    }
    var style = SpanStyle()
    if (span.bold) style = style.copy(fontWeight = FontWeight.Bold)
    if (span.italic) style = style.copy(fontStyle = FontStyle.Italic)
    if (span.strike) style = style.copy(textDecoration = TextDecoration.LineThrough)
    if (span.code) style = style.copy(fontFamily = FontFamily.Monospace)
    if (span.link != null) {
        style = style.copy(
            color = androidx.compose.ui.graphics.Color(0xFF2F6FB0),
            textDecoration = TextDecoration.Underline,
        )
        pushStringAnnotation(LINK_TAG, span.link)
        withStyle(style) { append(span.text) }
        pop()
        return
    }
    withStyle(style) { append(span.text) }
}
