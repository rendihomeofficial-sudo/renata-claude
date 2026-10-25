package com.renata.claude.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renata.claude.R

/**
 * Layar splash menggambar sendiri.
 *
 * Android 12+ sudah menampilkan splash screen sistem, layar ini hanya
 * melanjutkan dengan pemuatan yang halus. Untuk Android sebelum 12,
 * windowBackground sudah menampilkan logo yang sama sebelum Compose berjalan.
 */
@Composable
fun SplashScreen() {
    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(background, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                HaloOrb(color = primary)
                Image(
                    painter = painterResource(R.drawable.ic_splash_logo),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.size(112.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(280.dp),
            )
            Spacer(Modifier.height(26.dp))
            DotsLoader(color = primary)
        }
    }
}

/** Cincin cahaya berdenyut di belakang logo, seperti loader ala Claude. */
@Composable
private fun HaloOrb(color: Color) {
    val transition = rememberInfiniteTransition(label = "halo")
    val pulse by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "halo-pulse",
    )
    val alpha by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.34f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "halo-alpha",
    )
    Box(
        modifier = Modifier
            .size(150.dp)
            .scale(pulse)
            .alpha(alpha)
            .background(color, CircleShape),
    )
}

/** Tiga titik yang bergantian, penanda bahwa aplikasi sedang bekerja. */
@Composable
private fun DotsLoader(color: Color) {
    val transition = rememberInfiniteTransition(label = "dots")
    Box(
        modifier = Modifier.height(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { index ->
                val scale by transition.animateFloat(
                    initialValue = 0.55f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 520,
                            delayMillis = index * 170,
                            easing = FastOutSlowInEasing,
                        ),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "dot-$index",
                )
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .scale(scale)
                        .background(color, CircleShape),
                )
            }
        }
    }
}

/** Lingkaran tipis yang berputar perlahan, dipakai saat jawaban mulai mengalir. */
@Composable
fun ThinkingSpinner(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val sweep by transition.animateFloat(
        initialValue = 40f,
        targetValue = 320f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
        ),
        label = "spinner-sweep",
    )
    Canvas(modifier = modifier.size(18.dp)) {
        drawArc(
            color = color,
            startAngle = sweep,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(2f, 2f),
            size = Size(size.width - 4f, size.height - 4f),
            style = Stroke(width = 2.4f, cap = StrokeCap.Round),
        )
    }
}
