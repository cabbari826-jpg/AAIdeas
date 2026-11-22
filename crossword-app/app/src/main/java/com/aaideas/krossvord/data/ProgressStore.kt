package com.aaideas.krossvord.data

import android.content.Context
import com.aaideas.krossvord.model.Cell

/**
 * Local progress: finished levels, letters typed in unfinished levels, and counters.
 * SharedPreferences is enough for the prototype; the plan moves this to Room with packs.
 */
class ProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)

    fun completedLevels(): Set<String> = prefs.getStringSet(KEY_DONE, emptySet()).orEmpty()

    fun markCompleted(levelId: String) {
        prefs.edit().putStringSet(KEY_DONE, completedLevels() + levelId).remove("typed_$levelId")
            .remove("revealed_$levelId").remove("lives_$levelId").apply()
    }

    fun saveTyped(levelId: String, typed: Map<Cell, Char>, revealed: Set<Cell>) {
        prefs.edit()
            .putString("typed_$levelId", typed.entries.joinToString(";") { "${it.key.row},${it.key.col},${it.value}" })
            .putString("revealed_$levelId", revealed.joinToString(";") { "${it.row},${it.col}" })
            .apply()
    }

    fun loadTyped(levelId: String): Map<Cell, Char> =
        prefs.getString("typed_$levelId", null).orEmpty().split(';').filter { it.isNotBlank() }.mapNotNull {
            val p = it.split(',')
            if (p.size == 3 && p[2].length == 1) Cell(p[0].toInt(), p[1].toInt()) to p[2][0] else null
        }.toMap()

    fun loadRevealed(levelId: String): Set<Cell> =
        prefs.getString("revealed_$levelId", null).orEmpty().split(';').filter { it.isNotBlank() }.mapNotNull {
            val p = it.split(',')
            if (p.size == 2) Cell(p[0].toInt(), p[1].toInt()) else null
        }.toSet()

    fun resetLevel(levelId: String) {
        prefs.edit().remove("typed_$levelId").remove("revealed_$levelId").remove("clues_$levelId").remove("keyword_$levelId").remove("lives_$levelId").remove("tiers_$levelId")
            .putStringSet(KEY_DONE, completedLevels() - levelId).apply()
    }

    /** True when the level has letters on the board or a used clue/life (not yet finished). */
    fun hasProgress(levelId: String): Boolean =
        prefs.getString("typed_$levelId", null).orEmpty().isNotBlank() || prefs.contains("lives_$levelId")

    /** Forget every level's progress and counters; keep the player's settings. */
    fun resetAll() {
        val keep = setOf(KEY_INPUT, KEY_HIDE_CLUES, KEY_EXTRA, KEY_THEME, KEY_DYNAMIC, KEY_TUTORIAL, KEY_BIG_CLUES, KEY_SOUND)
        val e = prefs.edit()
        prefs.all.keys.filter { it !in keep }.forEach { e.remove(it) }
        e.apply()
    }

    var levelsSinceInterstitial: Int
        get() = prefs.getInt(KEY_SINCE_AD, 0)
        set(v) = prefs.edit().putInt(KEY_SINCE_AD, v).apply()

    /** Input mode name: COLUMNS (default), TILES or KEYBOARD. */
    var inputMode: String
        get() = prefs.getString(KEY_INPUT, null) ?: "COLUMNS"
        set(v) = prefs.edit().putString(KEY_INPUT, v).apply()

    /** Start each level with only one clue visible; the rest open on request. */
    var hideClues: Boolean
        get() = prefs.getBoolean(KEY_HIDE_CLUES, false)
        set(v) = prefs.edit().putBoolean(KEY_HIDE_CLUES, v).apply()

    /** Add a few random extra letters to the letter columns (harder). */
    var extraLetters: Boolean
        get() = prefs.getBoolean(KEY_EXTRA, false)
        set(v) = prefs.edit().putBoolean(KEY_EXTRA, v).apply()

    /** Look of the app: SYSTEM (follow the phone, default), LIGHT or DARK. */
    var themeMode: String
        get() = prefs.getString(KEY_THEME, null) ?: "SYSTEM"
        set(v) = prefs.edit().putString(KEY_THEME, v).apply()

    /** Take colours from the phone wallpaper (Android 12+) instead of the app's lavender. */
    var dynamicColor: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC, false)
        set(v) = prefs.edit().putBoolean(KEY_DYNAMIC, v).apply()

    /** Show the selected word's clue in large text under the grid (for low vision). */
    var bigClues: Boolean
        get() = prefs.getBoolean(KEY_BIG_CLUES, false)
        set(v) = prefs.edit().putBoolean(KEY_BIG_CLUES, v).apply()

    /** Chime and vibration for a found word or solved level. */
    var soundOn: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(v) = prefs.edit().putBoolean(KEY_SOUND, v).apply()

    /** Shared lives (see model/Lives); a new player gets the starting bonus. */
    var lives: com.aaideas.krossvord.model.Lives
        get() = if (!prefs.contains(KEY_LIVES)) com.aaideas.krossvord.model.Lives.newPlayer(System.currentTimeMillis())
            else com.aaideas.krossvord.model.Lives(prefs.getInt(KEY_LIVES, 3), prefs.getLong(KEY_LIVES_SINCE, 0L))
        set(v) = prefs.edit().putInt(KEY_LIVES, v.count).putLong(KEY_LIVES_SINCE, v.since).apply()

    /** Levels solved without a wrong word: count, current run, best run (for medals). */
    var perfectLevels: Int
        get() = prefs.getInt("perfect_levels", 0)
        set(v) = prefs.edit().putInt("perfect_levels", v).apply()
    var perfectRun: Int
        get() = prefs.getInt("perfect_run", 0)
        set(v) = prefs.edit().putInt("perfect_run", v).apply()
    var bestPerfectRun: Int
        get() = prefs.getInt("perfect_best", 0)
        set(v) = prefs.edit().putInt("perfect_best", v).apply()

    fun levelSeconds(levelId: String): Long = prefs.getLong("time_$levelId", 0L)
    fun addLevelSeconds(levelId: String, sec: Long) = prefs.edit().putLong("time_$levelId", levelSeconds(levelId) + sec).apply()

    /** Words the player has found (answers, and solved words of the day), for the collection. */
    var collection: Set<String>
        get() = prefs.getStringSet("collection", emptySet()).orEmpty().toSet()
        set(v) = prefs.edit().putStringSet("collection", HashSet(v)).apply()

    /** Seconds spent on the puzzle screen, for the statistics. */
    var playSeconds: Long
        get() = prefs.getLong(KEY_PLAY_SECONDS, 0L)
        set(v) = prefs.edit().putLong(KEY_PLAY_SECONDS, v).apply()

    /** Words found in all levels (counted as they are found). */
    var wordsFound: Int
        get() = prefs.getInt(KEY_WORDS_FOUND, 0)
        set(v) = prefs.edit().putInt(KEY_WORDS_FOUND, v).apply()

    /** The first-run tutorial (tap a clue cell to ease the clue) was finished or skipped. */
    var tutorialDone: Boolean
        get() = prefs.getBoolean(KEY_TUTORIAL, false)
        set(v) = prefs.edit().putBoolean(KEY_TUTORIAL, v).apply()

    /** Hints earned in game (e.g. key word bonus); spent before asking for an ad. */
    var freeHints: Int
        get() = prefs.getInt(KEY_FREE_HINTS, 0)
        set(v) = prefs.edit().putInt(KEY_FREE_HINTS, v).apply()

    fun saveKeywordGuessed(levelId: String) = prefs.edit().putBoolean("keyword_$levelId", true).apply()
    fun loadKeywordGuessed(levelId: String) = prefs.getBoolean("keyword_$levelId", false)

    fun saveLives(levelId: String, lives: Int) = prefs.edit().putInt("lives_$levelId", lives).apply()
    fun loadLives(levelId: String): Int? =
        if (prefs.contains("lives_$levelId")) prefs.getInt("lives_$levelId", 3) else null

    fun saveClueTiers(levelId: String, tiers: Map<Int, Int>) =
        prefs.edit().putString("tiers_$levelId", tiers.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()

    fun loadClueTiers(levelId: String): Map<Int, Int> =
        prefs.getString("tiers_$levelId", null).orEmpty().split(',').mapNotNull {
            val p = it.split(':'); if (p.size == 2) p[0].toIntOrNull()?.let { k -> p[1].toIntOrNull()?.let { v -> k to v } } else null
        }.toMap()

    fun saveOpenedClues(levelId: String, ids: Set<Int>) {
        prefs.edit().putString("clues_$levelId", ids.joinToString(",")).apply()
    }

    fun loadOpenedClues(levelId: String): Set<Int>? =
        prefs.getString("clues_$levelId", null)?.split(',')?.mapNotNull { it.toIntOrNull() }?.toSet()

    private companion object {
        const val KEY_INPUT = "input_mode"
        const val KEY_HIDE_CLUES = "hide_clues_v2"
        const val KEY_EXTRA = "extra_letters"
        const val KEY_THEME = "theme_mode"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_FREE_HINTS = "free_hints"
        const val KEY_TUTORIAL = "tutorial_clue_cells_done"
        const val KEY_BIG_CLUES = "big_clues"
        const val KEY_SOUND = "sound_on"
        const val KEY_LIVES = "lives_shared"
        const val KEY_LIVES_SINCE = "lives_since"
        const val KEY_PLAY_SECONDS = "play_seconds"
        const val KEY_WORDS_FOUND = "words_found"
        const val KEY_DONE = "completed"
        const val KEY_SINCE_AD = "levels_since_interstitial"
    }
}
