package com.renata.claude.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.renata.claude.data.Citation

/**
 * The sources a web search returned.
 *
 * The list is intentionally small: one row per source, numbered in the order
 * the provider returned them, and tappable so the page opens in the browser.
 */
@Composable
fun CitationList(
    citations: List<Citation>,
    onOpenLink: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (citations.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "Sumber",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
        )
        citations.forEachIndexed { index, citation ->
            SourceRow(
                index = index + 1,
                citation = citation,
                onOpen = onOpenLink,
            )
        }
    }
}

@Composable
private fun SourceRow(
    index: Int,
    citation: Citation,
    onOpen: ((String) -> Unit)?,
) {
    val domain = citation.url
        .substringAfter("://", citation.url)
        .substringBefore('/')
        .ifBlank { citation.url }
    Surface(
        onClick = { onOpen?.invoke(citation.url) },
        enabled = onOpen != null,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = "[$index]",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(26.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = citation.title.ifBlank { domain },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (domain != citation.title) {
                    Spacer(Modifier.size(2.dp))
                    Text(
                        text = domain,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
