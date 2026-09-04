package io.dmcc.pttkeypress.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

private val Aubergine = Color(0xFF7A3E65)
private val AubergineDark = Color(0xFF5E2E4D)
private val AubergineLight = Color(0xFFE6A8C7)

private val LightColors = lightColorScheme(
    primary = Aubergine,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF3DCE9),
    onPrimaryContainer = Color(0xFF4D1F3A),
    secondaryContainer = Color(0xFFF3DCE9),
    onSecondaryContainer = Color(0xFF4D1F3A),
    background = Color(0xFFFFFDF8),
    onBackground = Color(0xFF1C1B18),
    surface = Color(0xFFFFFDF8),
    onSurface = Color(0xFF1C1B18),
    surfaceVariant = Color(0xFFF1EEE6),
    onSurfaceVariant = Color(0xFF6E6A61),
    surfaceContainer = Color(0xFFF7F4EC),
    surfaceContainerHigh = Color(0xFFF1EEE6),
    surfaceContainerLow = Color(0xFFFBF9F2),
    outline = Color(0xFF9A948A),
    outlineVariant = Color(0xFFE4E0D6),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = AubergineLight,
    onPrimary = Color(0xFF42182F),
    primaryContainer = AubergineDark,
    onPrimaryContainer = Color(0xFFF5D7E7),
    secondaryContainer = Color(0xFF4A243C),
    onSecondaryContainer = Color(0xFFF5D7E7),
    background = Color(0xFF15130F),
    onBackground = Color(0xFFE6E1D8),
    surface = Color(0xFF15130F),
    onSurface = Color(0xFFE6E1D8),
    surfaceVariant = Color(0xFF23201B),
    onSurfaceVariant = Color(0xFF9A948A),
    surfaceContainer = Color(0xFF1B1813),
    surfaceContainerHigh = Color(0xFF23201B),
    surfaceContainerLow = Color(0xFF181510),
    outline = Color(0xFF6E6A61),
    outlineVariant = Color(0xFF2E2A24),
    error = Color(0xFFF2B8B5),
)

private val BaseTypography = Typography()
private val BrandTypography = Typography(
    displayLarge = BaseTypography.displayLarge.copy(fontFamily = FontFamily.Serif),
    displayMedium = BaseTypography.displayMedium.copy(fontFamily = FontFamily.Serif),
    displaySmall = BaseTypography.displaySmall.copy(fontFamily = FontFamily.Serif),
    headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = FontFamily.Serif),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = FontFamily.Serif),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = FontFamily.Serif),
    titleLarge = BaseTypography.titleLarge.copy(fontFamily = FontFamily.Serif),
)

@Composable
fun PttKeypressTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = BrandTypography,
        content = content,
    )
}
