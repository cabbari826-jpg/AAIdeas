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
        val keep = setOf(KEY_INPUT, KEY_HIDE_CLUES, KEY_EXTRA, KEY_THEME, KEY_DYNAMIC, KEY_TUTORIAL, KEY_BIG_CLUES, KEY_SOUND, KEY_SKIN, KEY_SKINS_OWNED, "large_mode", "kids_mode", "vibrate_on", "praise_voice", "reminder_asked", "launches", "whats_new_seen", "player_name", "birthday", "birth_year", "profile_asked", "birthday_gift_year")
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

    /** Chosen app/grid theme id (see ui.Skin). */
    var skin: String
        get() = prefs.getString(KEY_SKIN, null) ?: "klassik"
        set(v) = prefs.edit().putString(KEY_SKIN, v).apply()

    /** Gift themes already earned; kept even after the streak ends or the holiday passes. */
    var skinsOwned: Set<String>
        get() = prefs.getStringSet(KEY_SKINS_OWNED, emptySet()).orEmpty()
        set(v) = prefs.edit().putStringSet(KEY_SKINS_OWNED, v).apply()

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

    /** Experience points for the player's rank (model/Rank). */
    var xp: Int
        get() = prefs.getInt("xp", 0)
        set(v) = prefs.edit().putInt("xp", v).apply()

    /** Large mode: bigger text everywhere (see LARGE_FONT_SCALE). */
    /** The player's name for greetings; empty when not given. Kept on reset. */
    var playerName: String
        get() = prefs.getString("player_name", null).orEmpty()
        set(v) = prefs.edit().putString("player_name", v).apply()

    /** Birthday as "MM-dd", or null. Kept on reset. */
    var birthday: String?
        get() = prefs.getString("birthday", null)
        set(v) = prefs.edit().putString("birthday", v).apply()

    /** Birth year, 0 when not given. Kept on reset. */
    var birthYear: Int
        get() = prefs.getInt("birth_year", 0)
        set(v) = prefs.edit().putInt("birth_year", v).apply()

    /** The welcome (name, birthday) was shown once already. */
    var profileAsked: Boolean
        get() = prefs.getBoolean("profile_asked", false)
        set(v) = prefs.edit().putBoolean("profile_asked", v).apply()

    /** Year the birthday life was last given, so it comes once a year. */
    var birthdayGiftYear: Int
        get() = prefs.getInt("birthday_gift_year", 0)
        set(v) = prefs.edit().putInt("birthday_gift_year", v).apply()

    /** Day (epoch day) the daily welcome was last shown. */
    var welcomeDay: Long
        get() = prefs.getLong("welcome_day", -1)
        set(v) = prefs.edit().putLong("welcome_day", v).apply()

    /** Version code whose "what's new" the player has seen; 0 = none yet. Kept on reset. */
    var whatsNewSeen: Int
        get() = prefs.getInt("whats_new_seen", 0)
        set(v) = prefs.edit().putInt("whats_new_seen", v).apply()

    var launches: Int
        get() = prefs.getInt("launches", 0)
        set(v) = prefs.edit().putInt("launches", v).apply()

    var reminderAsked: Boolean
        get() = prefs.getBoolean("reminder_asked", false)
        set(v) = prefs.edit().putBoolean("reminder_asked", v).apply()

    var kidsMode: Boolean
        get() = prefs.getBoolean("kids_mode", false)
        set(v) = prefs.edit().putBoolean("kids_mode", v).apply()

    var largeMode: Boolean
        get() = prefs.getBoolean("large_mode", false)
        set(v) = prefs.edit().putBoolean("large_mode", v).apply()

    /** False until the player touches the large-mode switch; till then it follows the phone's font size. */
    val largeModeChosen: Boolean get() = prefs.contains("large_mode")

    /** Short buzz for a found word (separate from the sound). */
    var vibrateOn: Boolean
        get() = prefs.getBoolean("vibrate_on", true)
        set(v) = prefs.edit().putBoolean("vibrate_on", v).apply()

    /** Say "Bravo!" aloud for a found word, when the phone has an Azerbaijani voice. */
    var praiseVoice: Boolean
        get() = prefs.getBoolean("praise_voice", true)
        set(v) = prefs.edit().putBoolean("praise_voice", v).apply()

    /** Words the player got wrong at least once, for "Mənim sözlərim"; a word leaves when marked learned. */
    var mistakeWords: Set<String>
        get() = prefs.getStringSet("mistake_words", emptySet()).orEmpty().toSet()
        set(v) = prefs.edit().putStringSet("mistake_words", HashSet(v)).apply()

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
        const val KEY_SKIN = "skin"
        const val KEY_SKINS_OWNED = "skins_owned"
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
