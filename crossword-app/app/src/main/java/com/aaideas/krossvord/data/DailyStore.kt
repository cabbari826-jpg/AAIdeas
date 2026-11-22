package com.aaideas.krossvord.data

import android.content.Context
import com.aaideas.krossvord.model.TaskKind

/** Today's task counts, played days, the proverb and the fact counter. */
class DailyStore(context: Context) {
    private val prefs = context.getSharedPreferences("daily_extra", Context.MODE_PRIVATE)

    private fun rollDay(day: Long) {
        if (prefs.getLong("day", Long.MIN_VALUE) == day) return
        val e = prefs.edit().putLong("day", day)
        TaskKind.entries.forEach { e.remove("n_${it.name}") }
        e.remove("rewarded").remove("all_rewarded").remove("proverb").apply()
    }

    fun count(day: Long, kind: TaskKind): Int { rollDay(day); return prefs.getInt("n_${kind.name}", 0) }
    fun add(day: Long, kind: TaskKind, n: Int = 1) { rollDay(day); prefs.edit().putInt("n_${kind.name}", count(day, kind) + n).apply() }

    /** Task indexes already paid out today. */
    fun rewarded(day: Long): Set<Int> { rollDay(day); return prefs.getStringSet("rewarded", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet() }
    fun setRewarded(day: Long, s: Set<Int>) { rollDay(day); prefs.edit().putStringSet("rewarded", s.map { it.toString() }.toSet()).apply() }
    fun allRewarded(day: Long): Boolean { rollDay(day); return prefs.getBoolean("all_rewarded", false) }
    fun setAllRewarded(day: Long) { rollDay(day); prefs.edit().putBoolean("all_rewarded", true).apply() }

    /** The word picked for today's proverb, or null. */
    fun proverbPick(day: Long): String? { rollDay(day); return prefs.getString("proverb", null) }
    fun setProverbPick(day: Long, w: String) { rollDay(day); prefs.edit().putString("proverb", w).apply() }

    var playedDays: Set<Long>
        get() = prefs.getStringSet("played", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
        set(v) = prefs.edit().putStringSet("played", v.sortedDescending().take(400).map { it.toString() }.toSet()).apply()

    var lastStreakGift: Long
        get() = prefs.getLong("streak_gift", Long.MIN_VALUE)
        set(v) = prefs.edit().putLong("streak_gift", v).apply()

    var factIndex: Int
        get() = prefs.getInt("fact", 0)
        set(v) = prefs.edit().putInt("fact", v).apply()

    var miniSolvedDay: Long
        get() = prefs.getLong("mini_day", Long.MIN_VALUE)
        set(v) = prefs.edit().putLong("mini_day", v).apply()

    fun clear() = prefs.edit().clear().apply()
}
