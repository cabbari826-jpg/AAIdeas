package com.aaideas.krossvord.model

/** What medals are judged on; built from saved progress. */
data class MedalStats(
    val packsDone: Set<String> = emptySet(),
    val levelsDone: Int = 0,
    val perfectLevels: Int = 0,
    val bestPerfectRun: Int = 0,
    val dailyBestStreak: Int = 0,
    val wordsFound: Int = 0,
)

/**
 * A medal. [id] is stable: it becomes the Play Games achievement id later, so never rename one.
 */
data class Medal(val id: String, val icon: String, val title: String, val description: String, val earned: (MedalStats) -> Boolean)

object Medals {
    private val fixed = listOf(
        Medal("first_level", "🎉", "İlk addım", "İlk səviyyəni həll et") { it.levelsDone >= 1 },
        Medal("levels_10", "🥉", "Həvəskar", "10 səviyyə həll et") { it.levelsDone >= 10 },
        Medal("levels_50", "🥈", "Bilici", "50 səviyyə həll et") { it.levelsDone >= 50 },
        Medal("perfect_1", "🎯", "Səhvsiz", "Bir səviyyəni səhvsiz həll et") { it.perfectLevels >= 1 },
        Medal("perfect_10", "💎", "Dəqiq", "10 səviyyəni səhvsiz həll et") { it.perfectLevels >= 10 },
        Medal("perfect_run_3", "🔥", "Səhvsiz seriya", "Ardıcıl 3 səviyyə səhvsiz") { it.bestPerfectRun >= 3 },
        Medal("perfect_run_10", "⚡", "Qüsursuz", "Ardıcıl 10 səviyyə səhvsiz") { it.bestPerfectRun >= 10 },
        Medal("daily_3", "📅", "Üç gün", "Günün sözünü 3 gün ardıcıl tap") { it.dailyBestStreak >= 3 },
        Medal("daily_7", "🗓", "Həftə", "Günün sözünü 7 gün ardıcıl tap") { it.dailyBestStreak >= 7 },
        Medal("daily_30", "🏆", "Ay", "Günün sözünü 30 gün ardıcıl tap") { it.dailyBestStreak >= 30 },
        Medal("words_100", "📖", "Söz yığan", "100 söz tap") { it.wordsFound >= 100 },
        Medal("words_500", "📚", "Lüğət", "500 söz tap") { it.wordsFound >= 500 },
    )

    /** All medals: the fixed ones, then one per pack ("<pack> ustası"). */
    fun all(packs: List<Pack>): List<Medal> = fixed + packs.map { p ->
        Medal("pack_${p.packId}", "🏅", "${p.title} ustası", "«${p.title}» paketini tam həll et") { p.packId in it.packsDone }
    }

    fun earnedIds(packs: List<Pack>, stats: MedalStats): Set<String> = all(packs).filter { it.earned(stats) }.map { it.id }.toSet()
}
