package com.aaideas.krossvord.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
    /** Ornament drawn on blocked (empty) cells; NONE keeps them a flat colour. */
    val pattern: BlockPattern = BlockPattern.NONE,
    val patternInk: Color = Color.Transparent,
    val patternInk2: Color = Color.Transparent,
)

// The lavender the owner likes (Material 3 baseline), pinned so it no longer depends on library defaults.
internal val LightColors = lightColorScheme(
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
internal val DarkColors = darkColorScheme(
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

internal val LightGrid = GridColors(
    clueCell = Color(0xFFE9E3F2), clueText = Color(0xFF2E2640), emptyCell = Color(0xFFC2B8D2),
    letterCell = Color.White, letter = Color(0xFF1D1A22), activeWord = Color(0xFFFFF1C2),
    activeCell = Color(0xFFFFC94D), solved = Color(0xFFDDF3E3), wrong = Color(0xFFFFD9D6),
    revealed = Color(0xFF6750A4), line = Color(0xFF9A90AA), keyword = Color(0xFFD9480F),
)
internal val DarkGrid = GridColors(
    clueCell = Color(0xFF2A2533), clueText = Color(0xFFD6CFE2), emptyCell = Color(0xFF110F15),
    letterCell = Color(0xFF1F1C25), letter = Color(0xFFEDE7F3), activeWord = Color(0xFF3D3524),
    activeCell = Color(0xFF7A6020), solved = Color(0xFF1E3528), wrong = Color(0xFF4A2522),
    revealed = Color(0xFFCDB8FF), line = Color(0xFF3A3445), keyword = Color(0xFFF0A05A),
)

val LocalGridColors = staticCompositionLocalOf { LightGrid }

@Composable
fun KrossvordTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    skin: Skin = Skin.KLASSIK,
    content: @Composable () -> Unit,
) {
    val dark = skin.alwaysDark || when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = when {
        skin == Skin.KLASSIK && dynamicColor && supportsDynamicColor ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> skin.palette(dark).scheme
    }
    MaterialTheme(colorScheme = scheme, shapes = ExpressiveShapes, typography = ExpressiveType) {
        CompositionLocalProvider(LocalGridColors provides skin.palette(dark).grid, LocalGlass provides skin.glass) {
            content()
        }
    }
}

/** Wallpaper colours exist from Android 12. */
val supportsDynamicColor: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Material 3 Expressive look: rounder corners and heavier headings than the baseline. */
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/**
 * One typeface for the whole app (the owner's pick for the menu, 2026-11-23): Nunito, SIL OFL 1.1, subset to
 * Latin + Azerbaijani letters. Symbols it lacks (arrows, ✓) fall back to the system font per character.
 */
val AppFont = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(com.aaideas.krossvord.R.font.nunito_regular, FontWeight.Normal),
    androidx.compose.ui.text.font.Font(com.aaideas.krossvord.R.font.nunito_semibold, FontWeight.Medium),
    androidx.compose.ui.text.font.Font(com.aaideas.krossvord.R.font.nunito_semibold, FontWeight.SemiBold),
    androidx.compose.ui.text.font.Font(com.aaideas.krossvord.R.font.nunito_bold, FontWeight.Bold),
    androidx.compose.ui.text.font.Font(com.aaideas.krossvord.R.font.nunito_extrabold, FontWeight.ExtraBold),
    androidx.compose.ui.text.font.Font(com.aaideas.krossvord.R.font.nunito_extrabold, FontWeight.Black),
)

/** Material 3 type scale in [AppFont]; headings heavier than the baseline, labels never thin. */
private val ExpressiveType = Typography().run {
    fun androidx.compose.ui.text.TextStyle.f(w: FontWeight? = null) = copy(fontFamily = AppFont, fontWeight = w ?: fontWeight)
    copy(
        displayLarge = displayLarge.f(FontWeight.ExtraBold), displayMedium = displayMedium.f(FontWeight.ExtraBold),
        displaySmall = displaySmall.f(FontWeight.ExtraBold),
        headlineLarge = headlineLarge.f(FontWeight.ExtraBold), headlineMedium = headlineMedium.f(FontWeight.ExtraBold),
        headlineSmall = headlineSmall.f(FontWeight.Bold),
        titleLarge = titleLarge.f(FontWeight.Bold), titleMedium = titleMedium.f(FontWeight.Bold), titleSmall = titleSmall.f(FontWeight.Bold),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(FontWeight.Bold), labelMedium = labelMedium.f(FontWeight.Bold), labelSmall = labelSmall.f(FontWeight.SemiBold),
    )
}
