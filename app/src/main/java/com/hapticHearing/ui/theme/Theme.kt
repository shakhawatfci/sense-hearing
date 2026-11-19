package com.hapticHearing.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** High-contrast palettes. Colour is never the only signal; every colour use also has an icon or text. */
private val LightColors = lightColorScheme(
    primary = Color(0xFF0B3D91),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E2FF),
    onPrimaryContainer = Color(0xFF001A43),
    secondary = Color(0xFF6B4E00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE08A),
    onSecondaryContainer = Color(0xFF221A00),
    error = Color(0xFFB00020),
    onError = Color.White,
    background = Color.White,
    onBackground = Color(0xFF0A0A0A),
    surface = Color.White,
    onSurface = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFFE8EAF0),
    onSurfaceVariant = Color(0xFF1F232B),
    outline = Color(0xFF3A3F4A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF002E6A),
    primaryContainer = Color(0xFF0B3D91),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFFFD54F),
    onSecondary = Color(0xFF3A2C00),
    secondaryContainer = Color(0xFF574200),
    onSecondaryContainer = Color(0xFFFFE08A),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF410002),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1E2129),
    onSurfaceVariant = Color(0xFFE2E5EE),
    outline = Color(0xFFB0B6C3),
)

/** Band colours (always shown with a text label, never alone). */
@Immutable
data class BandColors(val low: Color, val mid: Color, val high: Color, val danger: Color) {
    operator fun get(band: Int) = when (band) { 0 -> low; 1 -> mid; else -> high }
}

val LocalBandColors = staticCompositionLocalOf {
    BandColors(Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFF8E24AA), Color(0xFFC62828))
}

/** Spec §10: minimum text size 18 sp. */
private val AppTypography = Typography().let { base ->
    fun TextStyle.atLeast(size: Int) = if (fontSize.value < size) copy(fontSize = size.sp, lineHeight = (size + 6).sp) else this
    base.copy(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold).atLeast(24),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold).atLeast(20),
        titleSmall = base.titleSmall.atLeast(18),
        bodyLarge = base.bodyLarge.atLeast(20),
        bodyMedium = base.bodyMedium.atLeast(18),
        bodySmall = base.bodySmall.atLeast(18),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold).atLeast(18),
        labelMedium = base.labelMedium.atLeast(18),
        labelSmall = base.labelSmall.atLeast(18),
    )
}

@Composable
fun HapticHearingTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val bandColors = if (darkTheme) {
        BandColors(Color(0xFF64B5F6), Color(0xFF81C784), Color(0xFFCE93D8), Color(0xFFFF8A80))
    } else {
        BandColors(Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFF8E24AA), Color(0xFFC62828))
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalBandColors provides bandColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
