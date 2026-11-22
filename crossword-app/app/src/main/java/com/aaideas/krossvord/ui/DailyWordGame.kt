package com.aaideas.krossvord.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aaideas.krossvord.model.DailyGuess
import com.aaideas.krossvord.model.DailyWord
import com.aaideas.krossvord.model.DailyWords
import com.aaideas.krossvord.monetization.AdService
import com.aaideas.krossvord.monetization.PremiumManager

/**
 * "Günün sözü": one hard word a day, guessed from its description. Kept apart from the
 * level progress (own preferences file) so it never mixes with packs.
 */
class DailyWordGame(
    context: Context,
    private val ads: AdService,
    private val premium: PremiumManager,
    private val today: () -> Long = { DailyWords.dayNumber() },
) {
    private val prefs = context.getSharedPreferences("daily_word", Context.MODE_PRIVATE)
    val words: List<DailyWord> =
        context.assets.open("word_of_day.json").bufferedReader(Charsets.UTF_8).use { DailyWords.parse(it.readText()) }

    var guess by mutableStateOf(load())
        private set
    var wrongFlash by mutableStateOf(false)
        private set
    /** What solving today's word gave (lives); shown on the screen. */
    var reward by mutableStateOf<String?>(null)

    /** Called when today's word is solved (chime, buzz, reward). */
    var onSolved: () -> Unit = {}

    /** Longest run of days in a row, and words of the day solved in all. */
    val bestStreak: Int get() = maxOf(prefs.getInt(KEY_BEST, 0), streak)
    val totalSolved: Int get() = prefs.getInt(KEY_TOTAL, 0)

    /** Days in a row solved, ending today or yesterday. */
    val streak: Int
        get() {
            val last = prefs.getLong(KEY_LAST_SOLVED, Long.MIN_VALUE)
            return if (last >= today() - 1) prefs.getInt(KEY_STREAK, 0) else 0
        }

    private fun load(): DailyGuess {
        val day = today()
        val word = DailyWords.forDay(words, day)
        if (prefs.getLong(KEY_DAY, Long.MIN_VALUE) != day) return DailyGuess(word)
        return DailyGuess(
            word,
            revealed = prefs.getString(KEY_REVEALED, "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }.toSet(),
            attempts = prefs.getInt(KEY_ATTEMPTS, 0),
            solved = prefs.getBoolean(KEY_SOLVED, false),
        )
    }

    /** Picks up a new day if the date changed while the app was open. */
    fun refresh() { if (prefs.getLong(KEY_DAY, Long.MIN_VALUE) != today()) guess = load() }

    private fun save(g: DailyGuess) {
        val e = prefs.edit().putLong(KEY_DAY, today())
            .putString(KEY_REVEALED, g.revealed.joinToString(","))
            .putInt(KEY_ATTEMPTS, g.attempts)
            .putBoolean(KEY_SOLVED, g.solved)
        if (g.solved && !guess.solved) {
            val last = prefs.getLong(KEY_LAST_SOLVED, Long.MIN_VALUE)
            val run = if (last == today() - 1) prefs.getInt(KEY_STREAK, 0) + 1 else 1
            e.putInt(KEY_STREAK, run).putLong(KEY_LAST_SOLVED, today())
                .putInt(KEY_BEST, maxOf(run, prefs.getInt(KEY_BEST, 0)))
                .putInt(KEY_TOTAL, prefs.getInt(KEY_TOTAL, 0) + 1)
            onSolved()
        }
        e.apply()
        guess = g
    }

    fun type(ch: Char) {
        wrongFlash = false
        val g = guess.type(ch)
        // A full word checks itself, like in the puzzle.
        if (g.boxes().none { it == null }) submit(g) else guess = g
    }

    fun backspace() { guess = guess.backspace() }

    private fun submit(g: DailyGuess) {
        val after = g.submit()
        wrongFlash = !after.solved
        save(after)
    }

    /** Opens a letter: free for premium, otherwise after a rewarded ad. */
    fun hint() {
        if (guess.finished) return
        if (premium.isPremium.value) save(guess.reveal())
        else ads.showRewarded("Günün sözü: hərf aç") { save(guess.reveal()) }
    }

    fun reset() { prefs.edit().clear().apply(); guess = load() }

    private companion object {
        const val KEY_DAY = "day"
        const val KEY_REVEALED = "revealed"
        const val KEY_ATTEMPTS = "attempts"
        const val KEY_SOLVED = "solved"
        const val KEY_STREAK = "streak"
        const val KEY_LAST_SOLVED = "last_solved"
        const val KEY_BEST = "best_streak"
        const val KEY_TOTAL = "total_solved"
    }
}
