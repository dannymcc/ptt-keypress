package io.dmcc.pttkeypress.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Plum = Color(0xFF9B6DFF)
private val DeepPlum = Color(0xFF6F43C0)
private val DarkBackground = Color(0xFF120F16)
private val DarkSurface = Color(0xFF1C1821)
private val LightBackground = Color(0xFFF8F6FA)

private val DarkColors = darkColorScheme(
    primary = Plum, onPrimary = Color(0xFF1B0E2E),
    secondary = Color(0xFFCDB7FF), background = DarkBackground,
    surface = DarkSurface, surfaceVariant = Color(0xFF29232F),
)
private val LightColors = lightColorScheme(
    primary = DeepPlum, onPrimary = Color.White,
    secondary = Color(0xFF7450A8), background = LightBackground,
    surface = Color.White, surfaceVariant = Color(0xFFF0EAF5),
)

@Composable
fun PttKeypressTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
