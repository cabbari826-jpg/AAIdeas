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
            .remove("revealed_$levelId").apply()
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
        prefs.edit().remove("typed_$levelId").remove("revealed_$levelId").remove("clues_$levelId")
            .putStringSet(KEY_DONE, completedLevels() - levelId).apply()
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
        get() = prefs.getBoolean(KEY_HIDE_CLUES, true)
        set(v) = prefs.edit().putBoolean(KEY_HIDE_CLUES, v).apply()

    fun saveOpenedClues(levelId: String, ids: Set<Int>) {
        prefs.edit().putString("clues_$levelId", ids.joinToString(",")).apply()
    }

    fun loadOpenedClues(levelId: String): Set<Int>? =
        prefs.getString("clues_$levelId", null)?.split(',')?.mapNotNull { it.toIntOrNull() }?.toSet()

    private companion object {
        const val KEY_INPUT = "input_mode"
        const val KEY_HIDE_CLUES = "hide_clues"
        const val KEY_DONE = "completed"
        const val KEY_SINCE_AD = "levels_since_interstitial"
    }
}
