package com.aaideas.krossvord.ui

import androidx.compose.foundation.background
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import java.util.Calendar

/** Ornament on the blocked cells of the grid. Drawn with a handful of shapes per cell, cheap on old phones. */
enum class BlockPattern { NONE, HATCH, STARS, CARPET, SEMENI, WAVES, POMEGRANATE, SEBEKE, BUTA, FLOWER }

/** How a gift theme is unlocked; shown under a locked theme in Settings. */
enum class Unlock(val hint: String, val rankId: String? = null) {
    FREE(""),
    RANK_1("«Həvəskar» rütbəsi (100 XP)", "rank_1"),
    RANK_2("«Bilici» rütbəsi (300 XP)", "rank_2"),
    STREAK_OR_RANK_3("Günün sözü 7 gün ardıcıl, ya da «Söz ustası» rütbəsi", "rank_3"),
    RANK_4("«Müdrik» rütbəsi (1500 XP)", "rank_4"),
    RANK_5("«Ustad» rütbəsi (3000 XP)", "rank_5"),
    NOVRUZ("Novruz bayramında hədiyyə (15 mart – 5 aprel)"),
    PREMIUM("Premium ilə"),
}

class SkinPalette(val scheme: ColorScheme, val grid: GridColors)

/**
 * App and grid themes. [id] is stored in settings, so never rename one.
 * Every palette keeps letters dark on light cells (or light on dark) with strong contrast for low vision.
 */
enum class Skin(
    val id: String,
    val label: String,
    val unlock: Unlock,
    val alwaysDark: Boolean = false,
    /** Liquid glass: a colourful backdrop behind see-through panels (blurred on Android 12+). */
    val glass: Boolean = false,
) {
    KLASSIK("klassik", "Klassik", Unlock.FREE),
    KAGIZ("kagiz", "Kağız", Unlock.FREE),
    GECE("gece", "Gecə", Unlock.FREE, alwaysDark = true),
    SUSE("suse", "Şüşə", Unlock.FREE, glass = true),
    QEZET("qezet", "Qəzet", Unlock.RANK_1),
    DENIZ("deniz", "Xəzər", Unlock.RANK_2),
    XALCA("xalca", "Xalça", Unlock.STREAK_OR_RANK_3),
    NAR("nar", "Nar", Unlock.RANK_4),
    QIZIL("qizil", "Qızıl", Unlock.RANK_5),
    NOVRUZ("novruz", "Novruz", Unlock.NOVRUZ),
    SEBEKE("sebeke", "Şəbəkə", Unlock.PREMIUM),
    BUTA("buta", "Buta", Unlock.PREMIUM),
    XARIBULBUL("xaribulbul", "Xarıbülbül", Unlock.PREMIUM, alwaysDark = true);

    fun palette(dark: Boolean): SkinPalette = when (this) {
        KLASSIK -> if (dark) SkinPalette(DarkColors, DarkGrid) else SkinPalette(LightColors, LightGrid)
        KAGIZ -> if (dark) KagizDark else KagizLight
        GECE -> GeceDark
        SUSE -> if (dark) GlassDark else GlassLight
        QEZET -> if (dark) QezetDark else QezetLight
        XALCA -> if (dark) XalcaDark else XalcaLight
        DENIZ -> if (dark) DenizDark else DenizLight
        NAR -> if (dark) NarDark else NarLight
        QIZIL -> if (dark) QizilDark else QizilLight
        NOVRUZ -> if (dark) NovruzDark else NovruzLight
        SEBEKE -> if (dark) SebekeDark else SebekeLight
        BUTA -> if (dark) ButaDark else ButaLight
        XARIBULBUL -> XaribulbulDark
    }

    companion object {
        fun fromId(id: String?): Skin = entries.firstOrNull { it.id == id } ?: KLASSIK
        val free: Set<Skin> = entries.filter { it.unlock == Unlock.FREE }.toSet()
        val premium: Set<Skin> = entries.filter { it.unlock == Unlock.PREMIUM }.toSet()

        /** Gift themes the player has earned right now (once earned, the caller keeps them for good). */
        fun earned(rankThemes: Set<String>, bestDailyStreak: Int, today: Calendar = Calendar.getInstance()): Set<Skin> = buildSet {
            addAll(free)
            entries.filter { it.unlock.rankId != null && it.unlock.rankId in rankThemes }.forEach { add(it) }
            if (bestDailyStreak >= 7) add(XALCA)
            if (isNovruz(today.get(Calendar.MONTH) + 1, today.get(Calendar.DAY_OF_MONTH))) add(NOVRUZ)
        }

        /** Novruz season, 15 March to 5 April. */
        fun isNovruz(month: Int, day: Int): Boolean = (month == 3 && day >= 15) || (month == 4 && day <= 5)
    }
}

private fun light(
    primary: Long, onPrimary: Long, pc: Long, onPc: Long, bg: Long, fg: Long,
    sv: Long, onSv: Long, sc: Long, sch: Long, outline: Long, ov: Long, tc: Long, onTc: Long,
) = lightColorScheme(
    primary = Color(primary), onPrimary = Color(onPrimary), primaryContainer = Color(pc), onPrimaryContainer = Color(onPc),
    secondary = Color(primary), onSecondary = Color(onPrimary), secondaryContainer = Color(pc), onSecondaryContainer = Color(onPc),
    tertiaryContainer = Color(tc), onTertiaryContainer = Color(onTc),
    background = Color(bg), onBackground = Color(fg), surface = Color(bg), onSurface = Color(fg),
    surfaceVariant = Color(sv), onSurfaceVariant = Color(onSv), surfaceContainer = Color(sc), surfaceContainerHigh = Color(sch),
    surfaceContainerLow = Color(sc), surfaceContainerHighest = Color(sch),
    outline = Color(outline), outlineVariant = Color(ov),
)

private fun dark(
    primary: Long, onPrimary: Long, pc: Long, onPc: Long, bg: Long, fg: Long,
    sv: Long, onSv: Long, sc: Long, sch: Long, outline: Long, ov: Long, tc: Long, onTc: Long,
) = darkColorScheme(
    primary = Color(primary), onPrimary = Color(onPrimary), primaryContainer = Color(pc), onPrimaryContainer = Color(onPc),
    secondary = Color(primary), onSecondary = Color(onPrimary), secondaryContainer = Color(pc), onSecondaryContainer = Color(onPc),
    tertiaryContainer = Color(tc), onTertiaryContainer = Color(onTc),
    background = Color(bg), onBackground = Color(fg), surface = Color(bg), onSurface = Color(fg),
    surfaceVariant = Color(sv), onSurfaceVariant = Color(onSv), surfaceContainer = Color(sc), surfaceContainerHigh = Color(sch),
    surfaceContainerLow = Color(sc), surfaceContainerHighest = Color(sch),
    outline = Color(outline), outlineVariant = Color(ov),
)

private fun grid(
    clue: Long, clueText: Long, block: Long, cell: Long, letter: Long, word: Long, cur: Long,
    ok: Long, bad: Long, revealed: Long, line: Long, key: Long,
    pattern: BlockPattern = BlockPattern.NONE, ink: Long = 0, ink2: Long = 0,
) = GridColors(
    clueCell = Color(clue), clueText = Color(clueText), emptyCell = Color(block), letterCell = Color(cell), letter = Color(letter),
    activeWord = Color(word), activeCell = Color(cur), solved = Color(ok), wrong = Color(bad), revealed = Color(revealed),
    line = Color(line), keyword = Color(key), pattern = pattern, patternInk = Color(ink), patternInk2 = Color(ink2),
)

// Şüşə (liquid glass): lavender, gold and mint light behind frosted white panels; at night deep violet and teal.
// Surfaces are see-through so the backdrop (GlassBackdrop) shows; the grid stays readable with near-white letter cells.
private val GlassLight = SkinPalette(
    light(0xFF6750A4, 0xFFFFFFFF, 0x99E8DEF8, 0xFF21005D, 0xFFE9E0F7, 0xFF1D1A22, 0x66FFFFFF, 0xFF3E3A4A, 0x59FFFFFF, 0x80FFFFFF, 0xFF79747E, 0x66FFFFFF, 0x99FFE8A8, 0xFF3B2A00)
        .copy(
            // Panels are the mockup's white glass; only the selected pack is a little whiter. Dialogs and pop-ups
            // (surface, surfaceContainerHigh/Highest) stay nearly solid so their text never sits over the grid.
            surface = Color(0xF7F6F1FF), surfaceContainerLowest = Color(0x33FFFFFF), surfaceContainerLow = Color(0x47FFFFFF),
            surfaceContainer = Color(0x47FFFFFF), surfaceContainerHigh = Color(0xF7F6F1FF), surfaceContainerHighest = Color(0xF7F6F1FF),
            surfaceVariant = Color(0x5CFFFFFF), secondaryContainer = Color(0x47FFFFFF), tertiaryContainer = Color(0x47FFFFFF),
            primaryContainer = Color(0x85FFFFFF), onSecondaryContainer = Color(0xFF1D1A22), onTertiaryContainer = Color(0xFF1D1A22),
        ),
    grid(0x8CE8DEF8, 0xFF2E2640, 0x596750A4, 0xD9FFFFFF, 0xFF1D1A22, 0xE6FFF1C2, 0xFFFFC94D, 0xE6DDF3E3, 0xE6FFD9D6, 0xFF6750A4, 0x80FFFFFF, 0xFFD9480F),
)
private val GlassDark = SkinPalette(
    dark(0xFFCDB8FF, 0xFF2B1F4A, 0x40CDB8FF, 0xFFF1EAFF, 0xFF15112A, 0xFFF1ECF8, 0x26FFFFFF, 0xFFD6CFE2, 0x1FFFFFFF, 0x2EFFFFFF, 0xFF9A90AA, 0x33FFFFFF, 0x40FFC94D, 0xFFFFF1C2)
        .copy(
            surface = Color(0xF7221C38), surfaceContainerLowest = Color(0x0FFFFFFF), surfaceContainerLow = Color(0x1FFFFFFF),
            surfaceContainer = Color(0x1FFFFFFF), surfaceContainerHigh = Color(0xF7221C38), surfaceContainerHighest = Color(0xF7221C38),
            surfaceVariant = Color(0x29FFFFFF), secondaryContainer = Color(0x1FFFFFFF), tertiaryContainer = Color(0x1FFFFFFF),
            primaryContainer = Color(0x38FFFFFF), onSecondaryContainer = Color(0xFFF1ECF8), onTertiaryContainer = Color(0xFFF1ECF8),
            onPrimaryContainer = Color(0xFFF1ECF8),
        ),
    grid(0x1FFFFFFF, 0xFFD6CFE2, 0x33000000, 0x29FFFFFF, 0xFFFFFFFF, 0x40FFC94D, 0xBFFFC94D, 0x662E9E5B, 0x66E5484D, 0xFFCDB8FF, 0x33FFFFFF, 0xFFF0A05A),
)

// Kağız: warm paper and brown ink; the blocked cells are pencil-hatched.
private val KagizLight = SkinPalette(
    light(0xFF7A4E2D, 0xFFFFFFFF, 0xFFF1DFC4, 0xFF3B2412, 0xFFF7F0E1, 0xFF2B2118, 0xFFEADFCB, 0xFF5C4B3B, 0xFFF1E8D6, 0xFFEADFCB, 0xFF8C7A66, 0xFFD6C7AE, 0xFFE9DCC0, 0xFF3B2412),
    grid(0xFFEFE4CC, 0xFF3B2A1A, 0xFFDCCBA8, 0xFFFFFCF4, 0xFF1F160D, 0xFFFBEBB8, 0xFFF2C255, 0xFFDCEBCF, 0xFFF6D2C8, 0xFF7A4E2D, 0xFFA89474, 0xFFB5481C,
        BlockPattern.HATCH, 0x66806446),
)
private val KagizDark = SkinPalette(
    dark(0xFFE3C29B, 0xFF3B2412, 0xFF4A3524, 0xFFF1DFC4, 0xFF1C1813, 0xFFEDE3D1, 0xFF2C251C, 0xFFCDBFA8, 0xFF241F18, 0xFF2E271F, 0xFF8F806B, 0xFF3D342A, 0xFF3D3224, 0xFFF1DFC4),
    grid(0xFF2E271D, 0xFFE6D9C2, 0xFF14110D, 0xFF241F18, 0xFFF5ECDC, 0xFF41361F, 0xFF7D6224, 0xFF23331F, 0xFF4A2A22, 0xFFE3C29B, 0xFF4A4034, 0xFFF0A066,
        BlockPattern.HATCH, 0x40C8B392),
)

// Gecə: deep night blue with a few stars on the blocked cells. Always dark.
private val GeceDark = SkinPalette(
    dark(0xFF9DBEFF, 0xFF0B1F45, 0xFF22325A, 0xFFDCE6FF, 0xFF0E1322, 0xFFE6ECFA, 0xFF1C2440, 0xFFB9C3DC, 0xFF151C30, 0xFF1D2540, 0xFF7C88A6, 0xFF2A3452, 0xFF2D2A52, 0xFFE4DEFF),
    grid(0xFF1C2542, 0xFFD5DEF2, 0xFF080B14, 0xFF161D33, 0xFFF0F4FF, 0xFF2C3557, 0xFF4F5F9E, 0xFF173A33, 0xFF4A2230, 0xFF9DBEFF, 0xFF2C3654, 0xFFFFC46B,
        BlockPattern.STARS, 0xCCFFE7A3, 0x80B9C8FF),
)

// Qəzet: newsprint, black squares and black ink, like the crossword page of a paper.
private val QezetLight = SkinPalette(
    light(0xFF1F1F1F, 0xFFFFFFFF, 0xFFE4E2DC, 0xFF111111, 0xFFF1EFE9, 0xFF141414, 0xFFE2E0DA, 0xFF4A4A4A, 0xFFE9E7E1, 0xFFE2E0DA, 0xFF777777, 0xFFCFCCC4, 0xFFE4E2DC, 0xFF111111),
    grid(0xFFE6E3DA, 0xFF1A1A1A, 0xFF161616, 0xFFFFFFFF, 0xFF0D0D0D, 0xFFEDEBE4, 0xFFFFD54F, 0xFFDDEBD9, 0xFFF4CFCB, 0xFF1F1F1F, 0xFF3C3C3C, 0xFFC62828),
)
private val QezetDark = SkinPalette(
    dark(0xFFEDEDED, 0xFF1A1A1A, 0xFF333333, 0xFFF2F2F2, 0xFF121212, 0xFFEDEDED, 0xFF242424, 0xFFBDBDBD, 0xFF1A1A1A, 0xFF242424, 0xFF8A8A8A, 0xFF333333, 0xFF333333, 0xFFF2F2F2),
    grid(0xFF262626, 0xFFE6E6E6, 0xFF000000, 0xFF1C1C1C, 0xFFF5F5F5, 0xFF3A3626, 0xFF8C7424, 0xFF1F3322, 0xFF4A2424, 0xFFEDEDED, 0xFF4A4A4A, 0xFFFF7961),
)

// Xalça: Azerbaijani carpet. Crimson field with ochre and indigo göl (diamond) motifs on the blocked cells.
private val XalcaLight = SkinPalette(
    light(0xFF9E1B32, 0xFFFFFFFF, 0xFFF5D9C9, 0xFF4A0A16, 0xFFF6EBDD, 0xFF2A1712, 0xFFEEDCC6, 0xFF5E4136, 0xFFF1E2CF, 0xFFEBD8C1, 0xFF8E6E5E, 0xFFD9BFA6, 0xFFDCE3F2, 0xFF1F3A68),
    grid(0xFFEBD3B5, 0xFF3A1A12, 0xFF9E1B32, 0xFFFFF8EE, 0xFF22110C, 0xFFFBE5B0, 0xFFE8B24A, 0xFFDDEBD0, 0xFFF6CFC9, 0xFF1F3A68, 0xFFA77C61, 0xFF1F3A68,
        BlockPattern.CARPET, 0xFFE0A93E, 0xFF1F3A68),
)
private val XalcaDark = SkinPalette(
    dark(0xFFFFB3B9, 0xFF5F0A1E, 0xFF5E1525, 0xFFFFD9DC, 0xFF1A0F12, 0xFFF2E2DC, 0xFF2E1D20, 0xFFD8C0BA, 0xFF23161A, 0xFF2E1D21, 0xFF9E827C, 0xFF45302F, 0xFF22304F, 0xFFD9E2FF),
    grid(0xFF3A2428, 0xFFF0DCD3, 0xFF6E1124, 0xFF2A1A1E, 0xFFFAEDE6, 0xFF4A3A22, 0xFF8E6A22, 0xFF213425, 0xFF4F2224, 0xFFAFC6FF, 0xFF4E3836, 0xFFF2C063,
        BlockPattern.CARPET, 0xFFC99234, 0xFF243F70),
)

// Novruz: spring green and gold; the blocked cells hold little semeni sprouts.
private val NovruzLight = SkinPalette(
    light(0xFF2E6B30, 0xFFFFFFFF, 0xFFCFE8C6, 0xFF0B2B0D, 0xFFF4F8EC, 0xFF18210F, 0xFFE2ECD6, 0xFF45523A, 0xFFEBF2E1, 0xFFE2ECD6, 0xFF73806A, 0xFFC4D2B8, 0xFFFFE9A8, 0xFF3D2E00),
    grid(0xFFE2EDD2, 0xFF1D2E12, 0xFF2E6B30, 0xFFFFFFFB, 0xFF132010, 0xFFFFF1BF, 0xFFF2C230, 0xFFD3EBC8, 0xFFF6D3CB, 0xFF2E6B30, 0xFF8FA383, 0xFFB86E00,
        BlockPattern.SEMENI, 0xFF9CD67A, 0xFFE6B422),
)
private val NovruzDark = SkinPalette(
    dark(0xFF9FD68F, 0xFF0B3A10, 0xFF234A22, 0xFFCFE8C6, 0xFF111710, 0xFFE3EBDA, 0xFF1F291C, 0xFFC0CCB6, 0xFF182016, 0xFF212B1E, 0xFF8A9881, 0xFF33402F, 0xFF4A3A00, 0xFFFFE9A8),
    grid(0xFF26331F, 0xFFDDE8D2, 0xFF153A17, 0xFF1B2318, 0xFFF0F6E8, 0xFF3D3A1E, 0xFF7E6A1A, 0xFF1F3A22, 0xFF4A2522, 0xFF9FD68F, 0xFF3A4734, 0xFFF2C230,
        BlockPattern.SEMENI, 0xFF6FB654, 0xFFD9A81E),
)

// Xəzər: Caspian blue-green with wave lines.
private val DenizLight = SkinPalette(
    light(0xFF00677D, 0xFFFFFFFF, 0xFFC4ECF5, 0xFF001F27, 0xFFF1F8FA, 0xFF111D21, 0xFFDCEBEF, 0xFF3F5257, 0xFFE6F1F4, 0xFFDCEBEF, 0xFF6F8287, 0xFFBFD2D7, 0xFFFFE3C2, 0xFF3A2100),
    grid(0xFFD9ECF0, 0xFF12323A, 0xFF1C6E80, 0xFFFFFFFF, 0xFF0C1A1E, 0xFFFFF0C4, 0xFFF4C04E, 0xFFD6EED8, 0xFFF7D3CD, 0xFF00677D, 0xFF8CA6AD, 0xFFC4561C,
        BlockPattern.WAVES, 0x99BFEFFA),
)
private val DenizDark = SkinPalette(
    dark(0xFF83D3E8, 0xFF00363F, 0xFF1C4A55, 0xFFC4ECF5, 0xFF0D1719, 0xFFE0EDF0, 0xFF1A2629, 0xFFBDCDD1, 0xFF131E21, 0xFF1C292C, 0xFF87999D, 0xFF2C3B3F, 0xFF4A3218, 0xFFFFE3C2),
    grid(0xFF1D2D31, 0xFFD4E6EA, 0xFF0B3640, 0xFF172225, 0xFFF0F8FA, 0xFF3B3620, 0xFF7E6620, 0xFF1C3324, 0xFF4A2522, 0xFF83D3E8, 0xFF2D3E42, 0xFFFFB27A,
        BlockPattern.WAVES, 0x8065C7DC),
)

// Nar: pomegranate red and leaf green.
private val NarLight = SkinPalette(
    light(0xFFA4243B, 0xFFFFFFFF, 0xFFFFD9DD, 0xFF40000F, 0xFFFFF7F6, 0xFF231918, 0xFFF4DDDD, 0xFF534343, 0xFFFBEDEC, 0xFFF4E2E1, 0xFF857373, 0xFFD8C2C1, 0xFFD5EBC8, 0xFF0F2006),
    grid(0xFFF6DEDD, 0xFF3D1518, 0xFFC93A4F, 0xFFFFFFFF, 0xFF1E0F10, 0xFFFFF0C4, 0xFFF2C14E, 0xFFDCEFD3, 0xFFF9D0CB, 0xFFA4243B, 0xFFB09A99, 0xFF3D7A2A,
        BlockPattern.POMEGRANATE, 0xFF7A0F22, 0xFF6DAA4F),
)
private val NarDark = SkinPalette(
    dark(0xFFFFB2BA, 0xFF670019, 0xFF5C1A26, 0xFFFFD9DD, 0xFF1A1112, 0xFFF0DEDE, 0xFF2D2021, 0xFFD7C1C1, 0xFF221718, 0xFF2D2122, 0xFFA08C8C, 0xFF43393A, 0xFF27391F, 0xFFD5EBC8),
    grid(0xFF3A2628, 0xFFF0DADA, 0xFF7A1A2C, 0xFF2A1B1D, 0xFFFBEDEE, 0xFF443A22, 0xFF8A6A20, 0xFF223424, 0xFF52262A, 0xFFFFB2BA, 0xFF4D3B3C, 0xFF9DD27F,
        BlockPattern.POMEGRANATE, 0xFF4A0814, 0xFF5E9444),
)

// Qızıl: deep teal with gold stars, the master's theme.
private val QizilLight = SkinPalette(
    light(0xFF7A5A00, 0xFFFFFFFF, 0xFFFFE08B, 0xFF261A00, 0xFFFFF9EE, 0xFF1F1B13, 0xFFEEE1CF, 0xFF4E4639, 0xFFF8EFE0, 0xFFF1E6D6, 0xFF807667, 0xFFD2C5B4, 0xFFC9E7E3, 0xFF00201E),
    grid(0xFFF3E6CC, 0xFF3A2C08, 0xFF0F4C48, 0xFFFFFFFF, 0xFF1A1405, 0xFFFFEDB5, 0xFFF0B92A, 0xFFDCEBD0, 0xFFF6D1CB, 0xFF7A5A00, 0xFFB4A58A, 0xFF0F4C48,
        BlockPattern.STARS, 0xFFF0C24A, 0xFFE8D7A8),
)
private val QizilDark = SkinPalette(
    dark(0xFFF2C14E, 0xFF3F2E00, 0xFF5B4300, 0xFFFFE08B, 0xFF141210, 0xFFEDE1CF, 0xFF262119, 0xFFD2C5B4, 0xFF1D1A15, 0xFF27231C, 0xFF9B8F7E, 0xFF3D372C, 0xFF14403C, 0xFFC9E7E3),
    grid(0xFF2E281C, 0xFFF0E3C8, 0xFF0A2E2B, 0xFF201C15, 0xFFFAF1DE, 0xFF463A1C, 0xFF9A7A1C, 0xFF1F3424, 0xFF4E2624, 0xFFF2C14E, 0xFF473F31, 0xFF7ED8CF,
        BlockPattern.STARS, 0xFFF2C14E, 0xFFBFA76A),
)

// Premium. Şəbəkə: Şəki stained-glass lattice, coloured panes in a wooden frame.
private val SebekeLight = SkinPalette(
    light(0xFF6B3E1E, 0xFFFFFFFF, 0xFFF6DCC6, 0xFF2C1406, 0xFFFBF5EE, 0xFF22170F, 0xFFF0E2D3, 0xFF54433A, 0xFFF6EBDF, 0xFFEFE1D2, 0xFF86736A, 0xFFD8C3B5, 0xFFD3E4FF, 0xFF0B2A5C),
    grid(0xFFF0DFCB, 0xFF3A2414, 0xFF5A3418, 0xFFFFFFFF, 0xFF1C120A, 0xFFFFF0C4, 0xFFF2BE3C, 0xFFDCEFD3, 0xFFF9D0CB, 0xFF1E5AA8, 0xFFA48A76, 0xFFC0362C,
        BlockPattern.SEBEKE, 0xFF2F7DD1, 0xFFE24A3B),
)
private val SebekeDark = SkinPalette(
    dark(0xFFF2BC93, 0xFF4A2408, 0xFF5A3A22, 0xFFF6DCC6, 0xFF17110D, 0xFFEFE2D8, 0xFF2A201A, 0xFFD8C3B5, 0xFF201813, 0xFF2B211B, 0xFF9F8C82, 0xFF3F332C, 0xFF1C3560, 0xFFD3E4FF),
    grid(0xFF33271F, 0xFFEFDFD0, 0xFF2E1A0C, 0xFF241B15, 0xFFFAF1E9, 0xFF453A20, 0xFF8E6C1C, 0xFF20341F, 0xFF4F2420, 0xFF8FB8F2, 0xFF4A3B31, 0xFFFF8A75,
        BlockPattern.SEBEKE, 0xFF2563A8, 0xFFB83A2E),
)

// Premium. Buta: the paisley of Azerbaijani textiles, gold on deep blue.
private val ButaLight = SkinPalette(
    light(0xFF1F3E8C, 0xFFFFFFFF, 0xFFDAE2FF, 0xFF001849, 0xFFF8F8FF, 0xFF191B23, 0xFFE1E2EC, 0xFF44464F, 0xFFEFF0FA, 0xFFE7E8F2, 0xFF757780, 0xFFC5C6D0, 0xFFFFE08B, 0xFF261A00),
    grid(0xFFE3E7F6, 0xFF14234A, 0xFF1F3E8C, 0xFFFFFFFF, 0xFF0E1530, 0xFFFFF0C4, 0xFFF2C14E, 0xFFDCEFD3, 0xFFF9D0CB, 0xFF1F3E8C, 0xFF9FA6BE, 0xFFB8860B,
        BlockPattern.BUTA, 0xFFE8B83A, 0xFFC0392B),
)
private val ButaDark = SkinPalette(
    dark(0xFFB3C5FF, 0xFF002B75, 0xFF243A75, 0xFFDAE2FF, 0xFF10131C, 0xFFE2E2EE, 0xFF1E2230, 0xFFC5C6D0, 0xFF171A24, 0xFF20242F, 0xFF8F909A, 0xFF33374A, 0xFF4A3A00, 0xFFFFE08B),
    grid(0xFF222A42, 0xFFDCE3F7, 0xFF0F1F4A, 0xFF181D2B, 0xFFF1F3FC, 0xFF3F3A22, 0xFF86701E, 0xFF1F3424, 0xFF4E2424, 0xFFB3C5FF, 0xFF343C55, 0xFFF2C14E,
        BlockPattern.BUTA, 0xFFD9A82E, 0xFFA8342A),
)

// Premium. Xarıbülbül: the Shusha flower on deep emerald with gold, always dark.
private val XaribulbulDark = SkinPalette(
    dark(0xFFE9C46A, 0xFF3B2A00, 0xFF2E4B3C, 0xFFDCEFE2, 0xFF0C1512, 0xFFE4EEE8, 0xFF19261F, 0xFFC0CFC6, 0xFF111C17, 0xFF1A2721, 0xFF8A9A91, 0xFF2A3A32, 0xFF3D2140, 0xFFF6D9FA),
    grid(0xFF1B2A23, 0xFFDDEBE3, 0xFF07110D, 0xFF14201A, 0xFFF2F8F4, 0xFF3C3A20, 0xFF8A701C, 0xFF1D3A2A, 0xFF4E2226, 0xFFE9C46A, 0xFF2A3B33, 0xFFE9C46A,
        BlockPattern.FLOWER, 0xFF8E3A8C, 0xFFE9C46A),
)

/** Background of a blocked cell, with its theme's ornament. */
fun Modifier.blockCell(c: GridColors): Modifier = when (c.pattern) {
    BlockPattern.NONE -> background(c.emptyCell)
    else -> clipToBounds().background(c.emptyCell).drawWithCache {
        val w = size.width; val h = size.height; val m = minOf(w, h)
        val ink = c.patternInk; val ink2 = c.patternInk2
        when (c.pattern) {
            BlockPattern.HATCH -> {
                val step = m / 5
                val stroke = Stroke(width = m * 0.035f)
                onDrawBehind {
                    var x = -h
                    while (x < w) { drawLine(ink, Offset(x, h), Offset(x + h, 0f), stroke.width); x += step }
                }
            }
            BlockPattern.STARS -> onDrawBehind {
                drawCircle(ink, m * 0.05f, Offset(w * 0.28f, h * 0.3f))
                drawCircle(ink2, m * 0.03f, Offset(w * 0.7f, h * 0.22f))
                drawCircle(ink2, m * 0.035f, Offset(w * 0.62f, h * 0.72f))
            }
            BlockPattern.CARPET -> {
                // A göl: outer ochre diamond, inner indigo diamond, ochre heart, and four corner ticks.
                fun diamond(r: Float) = Path().apply {
                    moveTo(w / 2, h / 2 - r); lineTo(w / 2 + r, h / 2); lineTo(w / 2, h / 2 + r); lineTo(w / 2 - r, h / 2); close()
                }
                val outer = diamond(m * 0.40f); val inner = diamond(m * 0.26f); val heart = diamond(m * 0.10f)
                val tick = m * 0.12f
                onDrawBehind {
                    drawPath(outer, ink)
                    drawPath(inner, ink2)
                    drawPath(heart, ink)
                    listOf(Offset(0f, 0f), Offset(w, 0f), Offset(0f, h), Offset(w, h)).forEach { o ->
                        drawCircle(ink2, tick * 0.5f, o)
                    }
                }
            }
            BlockPattern.SEMENI -> {
                // Three sprouts growing from a small gold dish.
                val stroke = m * 0.07f
                onDrawBehind {
                    val base = h * 0.7f
                    listOf(-0.16f, 0f, 0.16f).forEachIndexed { i, dx ->
                        val x = w / 2 + dx * w
                        val top = h * (if (i == 1) 0.22f else 0.32f)
                        drawLine(ink, Offset(x, base), Offset(x + dx * w * 0.4f, top), stroke)
                    }
                    drawRect(ink2, Offset(w * 0.24f, base), androidx.compose.ui.geometry.Size(w * 0.52f, h * 0.1f))
                }
            }
            BlockPattern.WAVES -> {
                val stroke = Stroke(width = m * 0.06f)
                fun wave(y: Float) = Path().apply {
                    moveTo(0f, y)
                    quadraticTo(w * 0.25f, y - h * 0.12f, w * 0.5f, y)
                    quadraticTo(w * 0.75f, y + h * 0.12f, w, y)
                }
                val waves = listOf(wave(h * 0.33f), wave(h * 0.66f))
                onDrawBehind { waves.forEach { drawPath(it, ink, style = stroke) } }
            }
            BlockPattern.POMEGRANATE -> onDrawBehind {
                drawCircle(ink, m * 0.26f, Offset(w / 2, h * 0.56f))
                val crown = Path().apply {
                    moveTo(w * 0.40f, h * 0.34f); lineTo(w * 0.44f, h * 0.22f); lineTo(w * 0.50f, h * 0.30f)
                    lineTo(w * 0.56f, h * 0.22f); lineTo(w * 0.60f, h * 0.34f); close()
                }
                drawPath(crown, ink)
                drawCircle(ink2, m * 0.07f, Offset(w * 0.74f, h * 0.26f))
            }
            BlockPattern.SEBEKE -> {
                // A star-shaped glass pane between wooden bars: two crossing squares and a centre.
                fun square(r: Float, rot: Boolean) = Path().apply {
                    if (rot) { moveTo(w / 2, h / 2 - r); lineTo(w / 2 + r, h / 2); lineTo(w / 2, h / 2 + r); lineTo(w / 2 - r, h / 2) }
                    else { val q = r * 0.72f; moveTo(w / 2 - q, h / 2 - q); lineTo(w / 2 + q, h / 2 - q); lineTo(w / 2 + q, h / 2 + q); lineTo(w / 2 - q, h / 2 + q) }
                    close()
                }
                val a = square(m * 0.42f, true); val b = square(m * 0.42f, false)
                val c = Offset(w / 2, h / 2)
                onDrawBehind {
                    drawPath(b, ink); drawPath(a, ink2)
                    drawCircle(Color(0xFFF2C14E), m * 0.1f, c)
                }
            }
            BlockPattern.BUTA -> {
                // Paisley: a teardrop whose tip curls, with a small dot inside.
                val p = Path().apply {
                    moveTo(w * 0.50f, h * 0.84f)
                    cubicTo(w * 0.18f, h * 0.80f, w * 0.20f, h * 0.38f, w * 0.48f, h * 0.36f)
                    cubicTo(w * 0.62f, h * 0.35f, w * 0.66f, h * 0.22f, w * 0.58f, h * 0.14f)
                    cubicTo(w * 0.86f, h * 0.22f, w * 0.86f, h * 0.70f, w * 0.50f, h * 0.84f)
                    close()
                }
                onDrawBehind { drawPath(p, ink); drawCircle(ink2, m * 0.08f, Offset(w * 0.48f, h * 0.6f)) }
            }
            BlockPattern.FLOWER -> onDrawBehind {
                // Xarıbülbül: three petals (purple) over a gold stem.
                drawLine(ink2, Offset(w / 2, h * 0.86f), Offset(w / 2, h * 0.5f), m * 0.05f)
                drawCircle(ink, m * 0.14f, Offset(w * 0.36f, h * 0.42f))
                drawCircle(ink, m * 0.14f, Offset(w * 0.64f, h * 0.42f))
                drawCircle(ink, m * 0.15f, Offset(w * 0.5f, h * 0.30f))
                drawCircle(ink2, m * 0.06f, Offset(w * 0.5f, h * 0.42f))
            }
            BlockPattern.NONE -> onDrawBehind {}
        }
    }
}
