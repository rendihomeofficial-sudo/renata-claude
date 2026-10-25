package com.renata.claude.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFD97757),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF2DFD7),
    onPrimaryContainer = Color(0xFF3B1D12),
    secondary = Color(0xFF5F6C66),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE6E1),
    onSecondaryContainer = Color(0xFF1B211F),
    background = Color(0xFFF5F4EE),
    onBackground = Color(0xFF262624),
    surface = Color(0xFFFFFBF8),
    onSurface = Color(0xFF262624),
    surfaceVariant = Color(0xFFE9E7E0),
    onSurfaceVariant = Color(0xFF5F5C55),
    outline = Color(0xFFB7B3AA),
    outlineVariant = Color(0xFFDEDBD3),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE08A6B),
    onPrimary = Color(0xFF3B1D12),
    primaryContainer = Color(0xFF6A3A28),
    onPrimaryContainer = Color(0xFFF7DCD1),
    secondary = Color(0xFFB7CCC4),
    onSecondary = Color(0xFF22302B),
    secondaryContainer = Color(0xFF3B4B45),
    onSecondaryContainer = Color(0xFFD6E8E0),
    background = Color(0xFF262624),
    onBackground = Color(0xFFF0EEE8),
    surface = Color(0xFF1F1E1C),
    onSurface = Color(0xFFF0EEE8),
    surfaceVariant = Color(0xFF3A3936),
    onSurfaceVariant = Color(0xFFC6C3BA),
    outline = Color(0xFF8E8A81),
    outlineVariant = Color(0xFF444340),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

@Composable
fun RenataClaudeTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
