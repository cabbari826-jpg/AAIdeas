package com.aaideas.krossvord.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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

private val LightGrid = GridColors(
    clueCell = Color(0xFFE3E8EF), clueText = Color(0xFF243447), emptyCell = Color(0xFFB8C2CF),
    letterCell = Color.White, letter = Color(0xFF14202E), activeWord = Color(0xFFFFF1C2),
    activeCell = Color(0xFFFFC94D), solved = Color(0xFFDDF3E3), wrong = Color(0xFFFFD9D6),
    revealed = Color(0xFF2F6FB5), line = Color(0xFF8A97A8), keyword = Color(0xFFD9480F),
)
private val DarkGrid = GridColors(
    clueCell = Color(0xFF2A3442), clueText = Color(0xFFD8E1EC), emptyCell = Color(0xFF151B23),
    letterCell = Color(0xFF1E2630), letter = Color(0xFFF1F4F8), activeWord = Color(0xFF4A4127),
    activeCell = Color(0xFF8C6D1F), solved = Color(0xFF1F3B2A), wrong = Color(0xFF4A2522),
    revealed = Color(0xFF7FB2EB), line = Color(0xFF3B4757), keyword = Color(0xFFFFA94D),
)

val LocalGridColors = staticCompositionLocalOf { LightGrid }

@Composable
fun KrossvordTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        androidx.compose.runtime.CompositionLocalProvider(LocalGridColors provides if (dark) DarkGrid else LightGrid) {
            content()
        }
    }
}
