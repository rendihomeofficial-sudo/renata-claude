package com.renata.claude.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Layar kunci aplikasi.
 *
 * PIN diisi dengan papan ketik angka supaya tidak perlu keyboard sistem.
 * [onVerify] mengembalikan true bila PIN benar; layar mengunci ulang kalau
 * PIN salah dan memberi kesempatan mencoba lagi.
 */
@Composable
fun LockScreen(
    onVerify: (String) -> Boolean,
    onOpenAppSettings: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (pin.length < 4) {
            error = "PIN minimal 4 angka."
            return
        }
        if (onVerify(pin)) {
            pin = ""
            error = null
        } else {
            error = "PIN salah, coba lagi."
            pin = ""
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Aplikasi terkunci",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Masukkan PIN untuk membuka aplikasi.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(MAX_PIN_LENGTH) { index ->
                    val filled = index < pin.length
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(
                                if (filled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                width = 1.dp,
                                color = if (filled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape,
                            )
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = error.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))

            Keypad(
                onDigit = { digit ->
                    if (pin.length < MAX_PIN_LENGTH) {
                        pin += digit
                        error = null
                    }
                },
                onDelete = {
                    if (pin.isNotEmpty()) {
                        pin = pin.dropLast(1)
                        error = null
                    }
                },
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { submit() },
                enabled = pin.isNotEmpty(),
                modifier = Modifier.widthIn(min = 180.dp),
            ) {
                Text("Buka")
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onOpenAppSettings) {
                Text("Lupa PIN? Buka pengaturan aplikasi")
            }
            Text(
                text = "PIN disimpan hanya di perangkat ini. Kalau lupa, data aplikasi harus dihapus dari pengaturan sistem, dan semua percakapan ikut terhapus.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Papan ketik angka 3 x 4 dengan tombol hapus. */
@Composable
private fun Keypad(
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", DELETE_KEY),
    )
    Column(
        modifier = Modifier.widthIn(max = 280.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                row.forEach { key ->
                    Box(modifier = Modifier.weight(1f)) {
                        if (key.isEmpty()) {
                            Spacer(Modifier.height(56.dp))
                        } else if (key == DELETE_KEY) {
                            KeyButton(label = DELETE_KEY, onClick = onDelete)
                        } else {
                            KeyButton(label = key, onClick = { onDigit(key) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

/** Panjang PIN maksimum yang diterima papan ketik. */
private const val MAX_PIN_LENGTH = 8

/** Label tombol hapus pada papan ketik. */
private const val DELETE_KEY = "\u232B"
