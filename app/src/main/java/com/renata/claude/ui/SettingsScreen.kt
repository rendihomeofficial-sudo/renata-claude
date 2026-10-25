package com.renata.claude.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.renata.claude.data.AiClient
import com.renata.claude.data.AppSettings
import com.renata.claude.data.LockPrefs
import com.renata.claude.data.Providers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
    onClearChats: () -> Unit,
    onCheckUpdate: () -> Unit,
    onLockChanged: () -> Unit = {},
) {
    var showKey by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var hasPin by remember { mutableStateOf(LockPrefs.hasPin()) }
    var showPinDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionTitle("Penyedia AI")
            Text(
                text = "Pilih penyedia, lalu isi kunci API. Semua data tetap di perangkat ini.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Providers.presets.forEach { preset ->
                ProviderRow(
                    label = preset.label,
                    subtitle = preset.baseUrl.ifBlank { "Base URL sendiri" },
                    selected = settings.providerId == preset.id,
                    onClick = {
                        onChange {
                            it.copy(
                                providerId = preset.id,
                                baseUrl = preset.baseUrl,
                                apiFormat = preset.apiFormat,
                                authStyle = preset.authStyle,
                                model = preset.models.firstOrNull() ?: it.model,
                            )
                        }
                    },
                )
            }

            SectionTitle("Format API")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.apiFormat == Providers.FORMAT_ANTHROPIC,
                    onClick = { onChange { it.copy(apiFormat = Providers.FORMAT_ANTHROPIC) } },
                    label = { Text("Anthropic") },
                )
                FilterChip(
                    selected = settings.apiFormat == Providers.FORMAT_OPENAI,
                    onClick = { onChange { it.copy(apiFormat = Providers.FORMAT_OPENAI) } },
                    label = { Text("OpenAI") },
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Format OpenAI cocok untuk MiniMax, OpenRouter, Groq, Together, Ollama, LM Studio dan layanan lain yang memakai Chat Completions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text("Gaya autentikasi", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.authStyle == Providers.AUTH_X_API_KEY,
                    onClick = { onChange { it.copy(authStyle = Providers.AUTH_X_API_KEY) } },
                    label = { Text("x-api-key") },
                )
                FilterChip(
                    selected = settings.authStyle == Providers.AUTH_BEARER,
                    onClick = { onChange { it.copy(authStyle = Providers.AUTH_BEARER) } },
                    label = { Text("Bearer") },
                )
                FilterChip(
                    selected = settings.authStyle == Providers.AUTH_BOTH,
                    onClick = { onChange { it.copy(authStyle = Providers.AUTH_BOTH) } },
                    label = { Text("Keduanya") },
                )
            }

            SectionTitle("Koneksi")
            OutlinedTextField(
                value = settings.baseUrl,
                onValueChange = { value -> onChange { it.copy(baseUrl = value) } },
                label = { Text("Base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text(
                        if (settings.apiFormat == Providers.FORMAT_OPENAI) {
                            "Endpoint /chat/completions akan ditambahkan otomatis."
                        } else {
                            "Endpoint /v1/messages akan ditambahkan otomatis."
                        }
                    )
                },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = settings.apiKey,
                onValueChange = { value -> onChange { it.copy(apiKey = value.trim()) } },
                label = { Text("Kunci API") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(onClick = { showKey = !showKey }) {
                        Text(if (showKey) "Sembunyikan" else "Tampilkan")
                    }
                },
                supportingText = { Text(Providers.byId(settings.providerId)?.keyHint.orEmpty()) },
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    testing = true
                    testResult = "Menguji…"
                    scope.launch {
                        testResult = runCatching { AiClient.testConnection(settings) }
                            .getOrElse { "Gagal: ${it.message}" }
                        testing = false
                    }
                },
                enabled = !testing && settings.apiKey.isNotBlank(),
            ) {
                Text(if (testing) "Menguji…" else "Uji koneksi")
            }
            testResult?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionTitle("Model")
            OutlinedTextField(
                value = settings.model,
                onValueChange = { value -> onChange { it.copy(model = value) } },
                label = { Text("ID model") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    val suggestions = Providers.byId(settings.providerId)?.models.orEmpty()
                    Text(
                        if (suggestions.isEmpty()) {
                            "Contoh: claude-sonnet-4-5, MiniMax-M3.1-Flash, gpt-4.1"
                        } else {
                            "Pilihan: " + suggestions.joinToString(", ")
                        }
                    )
                },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = settings.systemPrompt,
                onValueChange = { value -> onChange { it.copy(systemPrompt = value) } },
                label = { Text("Prompt sistem") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
            )
            Spacer(Modifier.height(8.dp))
            ToggleRow(
                title = "Pemikiran panjang",
                subtitle = "Model berpikir dulu sebelum menjawab",
                checked = settings.thinking,
                onCheckedChange = { value -> onChange { it.copy(thinking = value) } },
            )
            if (settings.thinking) {
                OutlinedTextField(
                    value = settings.thinkingBudget.toString(),
                    onValueChange = { value ->
                        val parsed = value.filter { it.isDigit() }.toIntOrNull() ?: return@OutlinedTextField
                        onChange { it.copy(thinkingBudget = parsed.coerceIn(1024, 32768)) }
                    },
                    label = { Text("Kuota berpikir (token)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
            }

            SectionTitle("Sampling")
            LabeledSlider(
                label = "Token maksimum: ${settings.maxTokens}",
                value = settings.maxTokens.toFloat(),
                range = 256f..64000f,
                onValueChange = { value -> onChange { it.copy(maxTokens = value.toInt()) } },
            )
            LabeledSlider(
                label = "Temperature: ${"%.2f".format(settings.temperature)}",
                value = settings.temperature,
                range = 0f..2f,
                onValueChange = { value -> onChange { it.copy(temperature = value) } },
            )
            LabeledSlider(
                label = "Top P: ${"%.2f".format(settings.topP)}",
                value = settings.topP,
                range = 0f..1f,
                onValueChange = { value -> onChange { it.copy(topP = value) } },
            )

            SectionTitle("Tampilan & perilaku")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "Sistem", "light" to "Terang", "dark" to "Gelap").forEach { (mode, label) ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { onChange { it.copy(themeMode = mode) } },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            ToggleRow(
                title = "Kirim dengan Enter",
                subtitle = "Jika mati, Enter membuat baris baru",
                checked = settings.sendOnEnter,
                onCheckedChange = { value -> onChange { it.copy(sendOnEnter = value) } },
            )

            SectionTitle("Suara langsung")
            ToggleRow(
                title = "Bacakan jawaban",
                subtitle = "Jawaban dibacakan suara sambil ditulis",
                checked = settings.speakReplies,
                onCheckedChange = { value -> onChange { it.copy(speakReplies = value) } },
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = settings.voiceLanguage,
                onValueChange = { value -> onChange { it.copy(voiceLanguage = value.trim()) } },
                label = { Text("Bahasa suara (BCP-47)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Contoh id-ID atau en-US. Kosong mengikuti bahasa perangkat.") },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = settings.liveVoicePrompt,
                onValueChange = { value -> onChange { it.copy(liveVoicePrompt = value) } },
                label = { Text("Karakter suara langsung") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                supportingText = { Text("Peran yang dipakai suara saat Anda berbicara dengannya.") },
            )

            SectionTitle("Alat dan riset")
            ToggleRow(
                title = "Alat di perangkat",
                subtitle = "Jam, kalkulator, dan pembaca halaman web",
                checked = settings.toolsEnabled,
                onCheckedChange = { value -> onChange { it.copy(toolsEnabled = value) } },
            )
            Spacer(Modifier.height(6.dp))
            ToggleRow(
                title = "Riset web",
                subtitle = "Mencari di sisi penyedia, sumber ditampilkan di jawaban",
                checked = settings.researchEnabled,
                onCheckedChange = { value -> onChange { it.copy(researchEnabled = value) } },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Riset web hanya bisa dipakai oleh penyedia yang mendukungnya. Kalau tidak, jawaban tetap dijawab tanpa sumber.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("Keamanan")
            ToggleRow(
                title = "Kunci aplikasi dengan PIN",
                subtitle = if (hasPin) "PIN sudah diatur di perangkat ini" else "Belum ada PIN, atur dulu",
                checked = settings.lockEnabled,
                onCheckedChange = { value ->
                    when {
                        value && !hasPin -> showPinDialog = true
                        value -> onChange { it.copy(lockEnabled = true) }
                        else -> onChange { it.copy(lockEnabled = false) }
                    }
                },
            )
            if (hasPin) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showPinDialog = true }) {
                        Text(if (settings.lockEnabled) "Ubah PIN" else "Atur PIN")
                    }
                    OutlinedButton(
                        onClick = {
                            LockPrefs.clearPin()
                            hasPin = false
                            onChange { it.copy(lockEnabled = false) }
                            onLockChanged()
                        },
                    ) {
                        Text("Hapus PIN")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "PIN hanya disimpan di perangkat ini sebagai hash. Lupa PIN berarti data aplikasi harus dihapus dari pengaturan sistem.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionTitle("Lanjutan")
            OutlinedTextField(
                value = settings.extraHeaders,
                onValueChange = { value -> onChange { it.copy(extraHeaders = value) } },
                label = { Text("Header tambahan (JSON)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                supportingText = { Text("Contoh {\"Authorization\": \"Bearer …\"} untuk server gateway.") },
            )

            SectionTitle("Pembaruan")
            OutlinedTextField(
                value = settings.updateManifestUrl,
                onValueChange = { value -> onChange { it.copy(updateManifestUrl = value.trim()) } },
                label = { Text("URL manifest pembaruan") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text("Tautan langsung ke berkas version.json, contoh https://raw.githubusercontent.com/owner/repo/main/version.json")
                },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onCheckUpdate) {
                Text("Cek pembaruan sekarang")
            }

            SectionTitle("Data")
            Text(
                text = "Percakapan hanya disimpan di perangkat ini. Tidak ada akun, tidak ada login, dan tidak ada batas penggunaan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { confirmClear = true }) {
                Text("Hapus semua percakapan")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showPinDialog) {
        PinSetupDialog(
            onDismiss = { showPinDialog = false },
            onConfirm = { pin ->
                LockPrefs.setPin(pin)
                hasPin = true
                onChange { it.copy(lockEnabled = true) }
                onLockChanged()
                showPinDialog = false
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Hapus semua percakapan?") },
            text = { Text("Semua obrolan yang tersimpan di perangkat ini akan dihapus. Tindakan ini tidak dapat dibatalkan.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearChats()
                    confirmClear = false
                }) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Batal") }
            },
        )
    }
}

@Composable
private fun PinSetupDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Atur PIN") },
        text = {
            Column {
                Text(
                    text = "PIN Minimal 4 angka. Aplikasi akan terkunci saat dibuka lagi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { value ->
                        pin = value.filter { it.isDigit() }.take(8)
                        error = null
                    },
                    label = { Text("PIN baru") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { value ->
                        confirmation = value.filter { it.isDigit() }.take(8)
                        error = null
                    },
                    label = { Text("Ulangi PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        pin.length < 4 -> error = "PIN minimal 4 angka."
                        pin != confirmation -> error = "PIN tidak sama."
                        else -> onConfirm(pin)
                    }
                },
            ) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        },
    )
}

@Composable
private fun ProviderRow(
    label: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        tonalElevation = if (selected) 0.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Text(
                    text = "Dipakai",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(18.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = onValueChange,
                valueRange = range,
            )
        }
    }
}
