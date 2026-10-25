package com.renata.claude.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.renata.claude.R
import com.renata.claude.data.AppSettings
import com.renata.claude.data.Attachment
import com.renata.claude.data.Chat
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chat: Chat?,
    isStreaming: Boolean,
    settings: AppSettings,
    snackbarHostState: SnackbarHostState,
    onMenuClick: () -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onSend: (String, List<Attachment>) -> Unit,
    onStop: () -> Unit,
    onRegenerate: () -> Unit,
    onOpenLiveVoice: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<List<Attachment>>(emptyList()) }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var showMenu by remember { mutableStateOf(false) }

    // Tautan pada jawaban dan daftar sumber dibuka di browser.
    val openLink: (String) -> Unit = { url ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    val shareText: (String) -> Unit = { text ->
        runCatching {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, text)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, "Bagikan pesan")
            context.startActivity(shareIntent)
        }
    }

    val exportChat: (asMarkdown: Boolean) -> Unit = { asMarkdown ->
        if (chat != null && chat.messages.isNotEmpty()) {
            val content = if (asMarkdown) {
                com.renata.claude.data.ChatExporter.toMarkdown(chat)
            } else {
                com.renata.claude.data.ChatExporter.toPlainText(chat)
            }
            val title = chat.title.ifBlank { "Percakapan" }
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, content)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, "Ekspor / Bagikan: $title")
            context.startActivity(shareIntent)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val attachment = AttachmentFactory.fromUri(context, uri)
                if (attachment == null) {
                    snackbarHostState.showSnackbar("Berkas tidak bisa dibaca (maks 20 MB).")
                } else {
                    pending = pending + attachment
                }
            }
        }
    }

    val messageCount = chat?.messages?.size ?: 0
    LaunchedEffect(messageCount, isStreaming) {
        if (messageCount > 0) {
            listState.animateScrollToItem(messageCount - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Renata Claude", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = settings.model,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Percakapan")
                    }
                },
                actions = {
                    IconButton(onClick = onNewChat) {
                        Icon(Icons.Default.Add, contentDescription = "Percakapan baru")
                    }
                    if (chat != null && chat.messages.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Opsi percakapan")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Ekspor Markdown (.md)") },
                                    onClick = {
                                        showMenu = false
                                        exportChat(true)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Ekspor Teks (.txt)") },
                                    onClick = {
                                        showMenu = false
                                        exportChat(false)
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Pengaturan")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding(),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (chat == null || chat.messages.isEmpty()) {
                    EmptyState(onSuggestion = { onSend(it, emptyList()) })
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(chat.messages, key = { it.id }) { message ->
                            MessageBubble(
                                message = message,
                                isStreaming = isStreaming && message.role == "assistant",
                                onCopy = { clipboard.setText(AnnotatedString(it)) },
                                onShare = shareText,
                                onRetry = if (message.role == "assistant" && message == chat.messages.last()) {
                                    { onRegenerate() }
                                } else {
                                    null
                                },
                                onOpenLink = openLink,
                            )
                        }
                    }
                }
            }
            Composer(
                input = input,
                onInputChange = { input = it },
                pending = pending,
                onRemoveAttachment = { att -> pending = pending - att },
                onAttach = { picker.launch(arrayOf("image/*", "application/pdf", "text/*")) },
                onSend = {
                    if (isStreaming) return@Composer
                    onSend(input, pending)
                    input = ""
                    pending = emptyList()
                },
                onStop = onStop,
                isStreaming = isStreaming,
                sendOnEnter = settings.sendOnEnter,
                onVoiceClick = onOpenLiveVoice,
            )
        }
    }
}

@Composable
private fun EmptyState(onSuggestion: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Renata Claude", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Claude Anda sendiri. Kunci Anda sendiri. Server Anda sendiri. Tanpa akun, tanpa batas.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        listOf(
            "Jelaskan apa yang dilakukan aplikasi ini dengan sederhana.",
            "Buat unit test untuk fungsi Kotlin saya.",
            "Tulis email tindak lanjut yang sopan.",
        ).forEach { suggestion ->
            Surface(
                onClick = { onSuggestion(suggestion) },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                )
            }
        }
    }
}

@Composable
private fun Composer(
    input: String,
    onInputChange: (String) -> Unit,
    pending: List<Attachment>,
    onRemoveAttachment: (Attachment) -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isStreaming: Boolean,
    sendOnEnter: Boolean,
    onVoiceClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        if (pending.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                pending.forEach { att ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        ) {
                            Text(
                                text = "${att.name}",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(120.dp),
                            )
                            IconButton(onClick = { onRemoveAttachment(att) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Hapus lampiran",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                IconButton(onClick = onAttach, enabled = !isStreaming) {
                    Icon(Icons.Default.Add, contentDescription = "Lampirkan berkas")
                }
                IconButton(
                    onClick = onVoiceClick,
                    enabled = !isStreaming,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mic),
                        contentDescription = "Suara langsung",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Tulis pesan untuk Renata Claude…") },
                    maxLines = 6,
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = if (sendOnEnter) ImeAction.Send else ImeAction.Default,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSend = { if (sendOnEnter && !isStreaming) onSend() },
                    ),
                )
                Spacer(Modifier.width(4.dp))
                if (isStreaming) {
                    FilledIconButton(onClick = onStop) {
                        Icon(Icons.Default.Close, contentDescription = "Hentikan")
                    }
                } else {
                    FilledIconButton(
                        onClick = onSend,
                        enabled = input.isNotBlank() || pending.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Kirim")
                    }
                }
            }
        }
    }
}
