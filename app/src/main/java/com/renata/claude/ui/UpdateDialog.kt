package com.renata.claude.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.renata.claude.update.AppRelease
import java.io.File

/** Status layar pembaruan. */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class UpToDate(val release: AppRelease) : UpdateUiState
    data class Available(val release: AppRelease) : UpdateUiState
    data class Downloading(val release: AppRelease, val progress: Int) : UpdateUiState
    data class Ready(val release: AppRelease, val apk: File) : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}

@Composable
fun UpdateDialog(
    state: UpdateUiState,
    canInstall: Boolean,
    onDismiss: () -> Unit,
    onDownload: (AppRelease) -> Unit,
    onInstall: (File) -> Unit,
    onOpenInstallPermission: () -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        UpdateUiState.Idle -> Unit

        UpdateUiState.Checking -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Memeriksa pembaruan") },
            text = { Text("Mengambil manifest rilis…") },
            confirmButton = {},
        )

        is UpdateUiState.UpToDate -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Aplikasi sudah terbaru") },
            text = {
                Text("Versi ${state.release.versionName} adalah versi terbaru yang tersedia.")
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
        )

        is UpdateUiState.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Pembaruan tersedia") },
            text = {
                Column {
                    Text(
                        text = "Versi ${state.release.versionName}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (state.release.releaseNotes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.release.releaseNotes,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onDownload(state.release) }) { Text("Unduh") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Nanti") }
            },
        )

        is UpdateUiState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Mengunduh pembaruan") },
            text = {
                Column {
                    Text(
                        text = "Versi ${state.release.versionName} · ${state.progress}%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {},
        )

        is UpdateUiState.Ready -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Pembaruan siap dipasang") },
            text = {
                Column {
                    Text(
                        text = "Versi ${state.release.versionName} sudah diunduh.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (!canInstall) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Aplikasi perlu izin memasang aplikasi dari sumber ini. Aplikasi akan membuka pengaturan izin bila belum diizinkan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                if (canInstall) {
                    TextButton(onClick = { onInstall(state.apk) }) { Text("Pasang sekarang") }
                } else {
                    TextButton(onClick = onOpenInstallPermission) { Text("Buka pengaturan izin") }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Nanti") }
            },
        )

        is UpdateUiState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Pembaruan gagal") },
            text = { Text(state.message) },
            confirmButton = {
                TextButton(onClick = onRetry) { Text("Coba lagi") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Tutup") }
            },
        )
    }
}
