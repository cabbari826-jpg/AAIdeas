package com.aaideas.krossvord.model

/**
 * Player rank by experience points. XP comes from solved levels (more for more stars) and
 * the word of the day. Each new rank unlocks the theme [themeId] (themes live in the design code).
 */
data class Rank(val index: Int, val title: String, val minXp: Int, val themeId: String?)

object Ranks {
    val ALL = listOf(
        Rank(0, "Şagird", 0, null),
        Rank(1, "Həvəskar", 100, "rank_1"),
        Rank(2, "Bilici", 300, "rank_2"),
        Rank(3, "Söz ustası", 700, "rank_3"),
        Rank(4, "Müdrik", 1500, "rank_4"),
        Rank(5, "Ustad", 3000, "rank_5"),
    )

    /** XP for a solved level: 10, plus 5 per star (15 to 25). */
    fun levelXp(stars: Int) = 10 + 5 * stars.coerceIn(1, 3)
    const val DAILY_XP = 15

    fun of(xp: Int): Rank = ALL.last { xp >= it.minXp }
    fun next(xp: Int): Rank? = ALL.firstOrNull { it.minXp > xp }

    /** Themes unlocked by ranks reached so far. */
    fun unlockedThemes(xp: Int): List<String> = ALL.filter { xp >= it.minXp }.mapNotNull { it.themeId }
}
