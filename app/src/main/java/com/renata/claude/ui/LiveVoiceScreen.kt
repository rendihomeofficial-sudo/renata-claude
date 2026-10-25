package com.renata.claude.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.renata.claude.R
import com.renata.claude.data.AppSettings
import com.renata.claude.data.SettingsStore
import com.renata.claude.voice.LiveVoiceController
import com.renata.claude.voice.LiveVoiceState
import kotlinx.coroutines.flow.collect
import java.util.Locale

/**
 * Full screen live voice conversation.
 *
 * The screen owns the [LiveVoiceController] and hands it to the view model as
 * the sink for the streamed answer, so the reply can be spoken out loud while
 * it is still arriving.
 */
@Composable
fun LiveVoiceScreen(
    vm: AppViewModel,
    settings: AppSettings,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val controller = remember {
        LiveVoiceController(
            context = context,
            scope = scope,
            locale = settings.voiceLanguage.toLocaleOrNull(),
            initialSpeakReplies = settings.speakReplies,
            onSend = { text -> vm.sendLive(text) },
        )
    }
    val state by controller.state.collectAsState()
    // A plain local copy, because a delegated property cannot be smart cast.
    val sessionState = state
    val liveMessages by vm.liveMessages.collectAsState()
    var errorText by remember { mutableStateOf<String?>(null) }

    DisposableEffect(controller) {
        vm.attachLiveSink(controller)
        controller.start()
        onDispose {
            vm.attachLiveSink(null)
            controller.release()
            vm.resetLiveSession()
        }
    }

    // A failed turn keeps its message on screen while the microphone reopens.
    LaunchedEffect(sessionState) {
        if (sessionState is LiveVoiceState.Error) errorText = sessionState.message
    }

    // Keep the newest line of the conversation in view.
    LaunchedEffect(Unit) {
        snapshotFlow { scroll.maxValue }.collect { scroll.animateScrollTo(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    controller.stop()
                    onClose()
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Akhiri suara langsung")
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Suara langsung",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    val next = !controller.speakReplies
                    controller.speakReplies = next
                    SettingsStore.update { it.copy(speakReplies = next) }
                }) {
                    Icon(
                        painter = painterResource(
                            if (controller.speakReplies) {
                                R.drawable.ic_volume_up
                            } else {
                                R.drawable.ic_volume_off
                            }
                        ),
                        contentDescription = if (controller.speakReplies) {
                            "Bisukan jawaban"
                        } else {
                            "Bacakan jawaban"
                        },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            VoiceOrb(state = sessionState)

            Spacer(Modifier.height(22.dp))

            Text(
                text = statusLabel(sessionState),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = hintLabel(sessionState),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    liveMessages.forEach { message ->
                        if (message.text.isBlank()) return@forEach
                        LiveBubble(role = message.role, text = message.text)
                    }
                    errorText?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (liveMessages.none { it.text.isNotBlank() } && errorText == null) {
                        Text(
                            text = "Katakan apa saja. Saya mendengarkan.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Tekan X untuk keluar. Kunci Anda sendiri, server Anda sendiri, tanpa batas.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )
        }
    }
}

/**
 * One line of the live conversation: what was said and who said it.
 */
@Composable
private fun LiveBubble(role: String, text: String) {
    val fromUser = role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (fromUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.fillMaxWidth(0.88f),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * The breathing circle that reacts to what the session is doing.
 */
@Composable
private fun VoiceOrb(state: LiveVoiceState) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val ring by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
        ),
        label = "ring",
    )

    val active = when (state) {
        is LiveVoiceState.Listening -> true
        is LiveVoiceState.Thinking -> true
        is LiveVoiceState.Speaking -> true
        else -> false
    }
    val core = when (state) {
        is LiveVoiceState.Listening -> MaterialTheme.colorScheme.primary
        is LiveVoiceState.Thinking -> MaterialTheme.colorScheme.tertiary
        is LiveVoiceState.Speaking -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .scale(0.7f + ring * 0.45f)
                    .alpha((1f - ring) * 0.45f)
                    .background(core.copy(alpha = 0.6f), CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .size(150.dp)
                .scale(if (active) pulse else 0.9f)
                .background(
                    Brush.radialGradient(listOf(core.copy(alpha = 0.95f), core.copy(alpha = 0.35f))),
                    CircleShape,
                ),
        )
    }
}

private fun statusLabel(state: LiveVoiceState): String = when (state) {
    LiveVoiceState.Idle -> "Siap"
    LiveVoiceState.Preparing -> "Bersiap…"
    is LiveVoiceState.Listening -> "Mendengarkan…"
    LiveVoiceState.Thinking -> "Berpikir…"
    is LiveVoiceState.Speaking -> "Berbicara…"
    is LiveVoiceState.Error -> "Terjadi kesalahan"
}

private fun hintLabel(state: LiveVoiceState): String = when (state) {
    LiveVoiceState.Idle -> "Ucapkan sesuatu untuk memulai."
    LiveVoiceState.Preparing -> "Membuka mikrofon."
    is LiveVoiceState.Listening ->
        if (state.partial.isBlank()) "Silakan bicara seperti kepada teman."
        else state.partial
    LiveVoiceState.Thinking -> "Sebentar, saya berpikir tentang hal itu."
    is LiveVoiceState.Speaking -> "Silakan, saya mendengarkan."
    is LiveVoiceState.Error -> state.message
}

/** Parses a BCP-47 tag such as "id-ID" into a [Locale], or null when blank. */
private fun String.toLocaleOrNull(): Locale? {
    val tag = trim()
    if (tag.isEmpty()) return null
    val parts = tag.replace('_', '-').split('-')
    return if (parts.size >= 2) {
        Locale(parts[0], parts[1])
    } else {
        Locale(parts[0])
    }
}
