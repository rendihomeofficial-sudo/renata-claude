package com.renata.claude.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.renata.claude.BuildConfig
import com.renata.claude.data.Chat
import com.renata.claude.data.LockPrefs
import com.renata.claude.data.SettingsStore
import com.renata.claude.update.ApkInstaller
import com.renata.claude.update.AppRelease
import com.renata.claude.update.UpdateChecker
import com.renata.claude.update.UpdatePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val chats by vm.chats.collectAsState()
    val currentChat by vm.currentChat.collectAsState()
    val isStreaming by vm.isStreaming.collectAsState()
    val notice by vm.notice.collectAsState()
    val settings by SettingsStore.state.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    var showSettings by remember { mutableStateOf(false) }
    var showLiveVoice by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Chat?>(null) }
    var updateState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
    var canInstall by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Kunci aplikasi: buka aplikasi lagi setelah ditinggalkan beberapa waktu.
    var hasPin by remember { mutableStateOf(LockPrefs.hasPin()) }
    var locked by remember { mutableStateOf(settings.lockEnabled && hasPin) }
    var backgroundedAt by remember { mutableStateOf(0L) }
    val lockRequired = settings.lockEnabled && hasPin
    val lockRequiredState = rememberUpdatedState(lockRequired)

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showLiveVoice = true
    }

    fun openLiveVoice() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) showLiveVoice = true else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun manifestUrl(): String =
        settings.updateManifestUrl.ifBlank {
            BuildConfig.UPDATE_MANIFEST_URL.ifBlank {
                "https://raw.githubusercontent.com/rendihomeofficial-sudo/renata-claude/main/version.json"
            }
        }.trim()

    fun checkUpdate(manual: Boolean) {
        val url = manifestUrl()
        if (url.isBlank()) {
            if (manual) {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        "Isi URL manifest pembaruan di Pengaturan terlebih dahulu."
                    )
                }
            }
            return
        }
        if (manual) {
            updateState = UpdateUiState.Checking
        }
        scope.launch {
            try {
                val release = UpdateChecker.fetchRelease(url)
                UpdatePrefs.markChecked(context)
                if (release.isNewerThan(BuildConfig.VERSION_CODE)) {
                    // Selalu tampilkan dialog popup jika versi baru tersedia!
                    updateState = UpdateUiState.Available(release)
                } else if (manual) {
                    updateState = UpdateUiState.UpToDate(release)
                } else {
                    updateState = UpdateUiState.Idle
                }
            } catch (e: Exception) {
                if (manual) {
                    updateState = UpdateUiState.Failed(e.message ?: "Gagal memeriksa pembaruan.")
                } else {
                    // Diamkan jika pengecekan latar belakang gagal agar tidak mengganggu pengguna
                    updateState = UpdateUiState.Idle
                }
            }
        }
    }

    fun startDownload(release: AppRelease) {
        updateState = UpdateUiState.Downloading(release, 0)
        scope.launch {
            try {
                val dir = File(context.filesDir, "updates").apply { mkdirs() }
                val apk = File(dir, "renata-claude-${release.versionName}.apk")
                UpdateChecker.downloadApk(release.downloadUrl, apk) { progress ->
                    withContext(Dispatchers.Main) {
                        updateState = UpdateUiState.Downloading(release, progress)
                    }
                }
                withContext(Dispatchers.Main) { updateState = UpdateUiState.Ready(release, apk) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateState = UpdateUiState.Failed(e.message ?: "Unduhan gagal.")
                }
            }
        }
    }

    fun openInstallPermission() {
        try {
            context.startActivity(ApkInstaller.installPermissionIntent(context))
        } catch (e: Exception) {
            updateState = UpdateUiState.Failed("Tidak bisa membuka pengaturan izin.")
        }
    }

    fun installApk(apk: File) {
        if (!ApkInstaller.canRequestInstall(context)) {
            openInstallPermission()
            scope.launch {
                snackbarHostState.showSnackbar(
                    "Izinkan pemasangan aplikasi dari sumber ini, lalu coba lagi."
                )
            }
            return
        }
        if (!ApkInstaller.install(context, apk)) {
            updateState = UpdateUiState.Failed("Tidak bisa membuka installer aplikasi.")
        } else {
            updateState = UpdateUiState.Idle
        }
    }

    LaunchedEffect(notice) {
        val text = notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        vm.dismissNotice()
    }

    // Pemeriksaan pembaruan otomatis setiap kali aplikasi dibuka.
    LaunchedEffect(Unit) {
        checkUpdate(manual = false)
    }

    LaunchedEffect(updateState) {
        canInstall = ApkInstaller.canRequestInstall(context)
    }

    // Izin pasang aplikasi bisa berubah di Pengaturan sistem, jadi dicek lagi saat kembali.
    val view = LocalView.current
    DisposableEffect(view) {
        val owner = view.findViewTreeLifecycleOwner()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canInstall = ApkInstaller.canRequestInstall(context)
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    // Aplikasi terkunci lagi kalau ditinggalkan lebih lama dari ambang batas.
    DisposableEffect(view) {
        val owner = view.findViewTreeLifecycleOwner()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> backgroundedAt = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> {
                    val since = backgroundedAt
                    if (since > 0L &&
                        System.currentTimeMillis() - since > LOCK_GRACE_MS &&
                        lockRequiredState.value
                    ) {
                        locked = true
                    }
                }
                else -> Unit
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    LaunchedEffect(lockRequired) {
        if (!lockRequired) locked = false
    }

    val filtered = remember(chats, query) {
        if (query.isBlank()) chats else chats.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.messages.any { m -> m.text.contains(query, ignoreCase = true) }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    // Saat terkunci, isi aplikasi tidak dirender sama sekali supaya tidak
    // ada yang bisa diklik di belakang layar kunci.
    if (locked) {
        LockScreen(
            onVerify = { pin ->
                val ok = LockPrefs.verify(pin)
                if (ok) locked = false
                ok
            },
            onBiometricSuccess = {
                locked = false
            },
            onOpenAppSettings = {
                runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                    )
                }
            },
        )
    } else {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                ) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Renata Claude",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Kunci Anda sendiri. Server Anda sendiri. Tanpa batas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Cari judul atau isi pesan…") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Cari",
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Hapus pencarian",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        onClick = {
                            vm.newChat()
                            scope.launch { drawerState.close() }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Percakapan baru")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (query.isNotBlank()) "Tidak ada percakapan yang cocok dengan \"$query\""
                                else "Belum ada riwayat percakapan",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(filtered, key = { it.id }) { chat ->
                                ChatRow(
                                    chat = chat,
                                    searchQuery = query,
                                    active = currentChat?.id == chat.id,
                                    onOpen = {
                                        vm.openChat(chat.id)
                                        scope.launch { drawerState.close() }
                                    },
                                    onRename = { renaming = chat },
                                    onDelete = { vm.deleteChat(chat.id) },
                                    onPin = { vm.togglePin(chat.id) },
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                    Surface(
                        onClick = {
                            showSettings = true
                            scope.launch { drawerState.close() }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Pengaturan")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
    ) {
        if (showLiveVoice) {
            LiveVoiceScreen(
                vm = vm,
                settings = settings,
                onClose = { showLiveVoice = false },
            )
        } else if (showSettings) {
            SettingsScreen(
                settings = settings,
                onChange = { transform -> SettingsStore.update(transform) },
                onBack = { showSettings = false },
                onClearChats = { vm.clearAllChats() },
                onCheckUpdate = { checkUpdate(manual = true) },
                onLockChanged = {
                    hasPin = LockPrefs.hasPin()
                    if (!hasPin) locked = false
                },
            )
        } else {
            ChatScreen(
                chat = currentChat,
                isStreaming = isStreaming,
                settings = settings,
                snackbarHostState = snackbarHostState,
                onMenuClick = { scope.launch { drawerState.open() } },
                onNewChat = { vm.newChat() },
                onOpenSettings = { showSettings = true },
                onSend = { text, attachments -> vm.send(text, attachments) },
                onStop = { vm.stop() },
                onRegenerate = { vm.regenerate() },
                onOpenLiveVoice = { openLiveVoice() },
            )
        }
    }

    renaming?.let { chat ->
        RenameDialog(
            chat = chat,
            onDismiss = { renaming = null },
            onConfirm = { title ->
                vm.renameChat(chat.id, title)
                renaming = null
            },
        )
    }

    if (updateState !is UpdateUiState.Idle) {
        UpdateDialog(
            state = updateState,
            canInstall = canInstall,
            onDismiss = { updateState = UpdateUiState.Idle },
            onDownload = { startDownload(it) },
            onInstall = { installApk(it) },
            onOpenInstallPermission = { openInstallPermission() },
            onRetry = { checkUpdate(manual = true) },
        )
    }

    }
    }
}

/** Lamanya aplikasi boleh ditinggalkan sebelum terkunci lagi, dalam milidetik. */
private const val LOCK_GRACE_MS = 30_000L

@Composable
private fun ChatRow(
    chat: Chat,
    searchQuery: String = "",
    active: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(12.dp),
        color = if (active) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onPin, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Sematkan percakapan",
                        modifier = Modifier.size(16.dp),
                        tint = if (chat.pinned) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRename, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Ganti nama percakapan",
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Hapus percakapan",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            // Jika pengguna sedang mencari teks, cari potongan pesan yang cocok terlebih dahulu
            val matchedMessage = if (searchQuery.isNotBlank()) {
                chat.messages.firstOrNull { it.text.contains(searchQuery, ignoreCase = true) }?.text
            } else null

            val preview = (matchedMessage ?: chat.messages.lastOrNull { it.text.isNotBlank() }?.text).orEmpty()
            if (preview.isNotBlank()) {
                Text(
                    text = preview.replace(Regex("\\s+"), " "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (matchedMessage != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(
    chat: Chat,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember(chat.id) { mutableStateOf(chat.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ganti nama percakapan") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title) }) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        },
    )
}
