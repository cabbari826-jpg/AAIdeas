package com.aaideas.krossvord.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** What the player picked in Settings → Görünüş. */
enum class ThemeMode(val label: String) {
    SYSTEM("Sistem"), LIGHT("Açıq"), DARK("Qaranlıq");

    companion object {
        fun fromName(name: String): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/** Cell colours are design tokens so polishing later touches one place. */
@Immutable
data class GridColors(
    val clueCell: Color,
    val clueText: Color,
    val emptyCell: Color,
    val letterCell: Color,
    val letter: Color,
    val activeWord: Color,
    val activeCell: Color,
    val solved: Color,
    val wrong: Color,
    val revealed: Color,
    val line: Color,
    val keyword: Color,
)

// The lavender the owner likes (Material 3 baseline), pinned so it no longer depends on library defaults.
private val LightColors = lightColorScheme(
    primary = Color(0xFF6750A4), onPrimary = Color.White,
    primaryContainer = Color(0xFFE8DEF8), onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8), onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260), onTertiary = Color.White,
    background = Color(0xFFFDF7FF), onBackground = Color(0xFF1D1A22),
    surface = Color(0xFFFDF7FF), onSurface = Color(0xFF1D1A22),
    surfaceVariant = Color(0xFFE7E0EC), onSurfaceVariant = Color(0xFF49454F),
    surfaceContainer = Color(0xFFF3EDF7), surfaceContainerHigh = Color(0xFFECE6F0),
    outline = Color(0xFF79747E), outlineVariant = Color(0xFFCAC4D0),
    error = Color(0xFFB3261E), onError = Color.White,
)

// Evening palette: deep plum instead of black, off-white text, nothing glaring.
private val DarkColors = darkColorScheme(
    primary = Color(0xFFCDB8FF), onPrimary = Color(0xFF2B1F4A),
    primaryContainer = Color(0xFF3A3150), onPrimaryContainer = Color(0xFFE8DEF8),
    secondary = Color(0xFFCBC2DB), onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF2E2740), onSecondaryContainer = Color(0xFFE2DAF0),
    tertiary = Color(0xFFE5B8C6), onTertiary = Color(0xFF492532),
    background = Color(0xFF16141B), onBackground = Color(0xFFE6E0EC),
    surface = Color(0xFF16141B), onSurface = Color(0xFFE6E0EC),
    surfaceVariant = Color(0xFF2A2533), onSurfaceVariant = Color(0xFFC4BCD0),
    surfaceContainer = Color(0xFF1F1C25), surfaceContainerHigh = Color(0xFF29252F),
    outline = Color(0xFF8E8699), outlineVariant = Color(0xFF3A3445),
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
)

private val LightGrid = GridColors(
    clueCell = Color(0xFFE9E3F2), clueText = Color(0xFF2E2640), emptyCell = Color(0xFFC2B8D2),
    letterCell = Color.White, letter = Color(0xFF1D1A22), activeWord = Color(0xFFFFF1C2),
    activeCell = Color(0xFFFFC94D), solved = Color(0xFFDDF3E3), wrong = Color(0xFFFFD9D6),
    revealed = Color(0xFF6750A4), line = Color(0xFF9A90AA), keyword = Color(0xFFD9480F),
)
private val DarkGrid = GridColors(
    clueCell = Color(0xFF2A2533), clueText = Color(0xFFD6CFE2), emptyCell = Color(0xFF110F15),
    letterCell = Color(0xFF1F1C25), letter = Color(0xFFEDE7F3), activeWord = Color(0xFF3D3524),
    activeCell = Color(0xFF7A6020), solved = Color(0xFF1E3528), wrong = Color(0xFF4A2522),
    revealed = Color(0xFFCDB8FF), line = Color(0xFF3A3445), keyword = Color(0xFFF0A05A),
)

val LocalGridColors = staticCompositionLocalOf { LightGrid }

@Composable
fun KrossvordTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors) {
        CompositionLocalProvider(LocalGridColors provides if (dark) DarkGrid else LightGrid) {
            content()
        }
    }
}
