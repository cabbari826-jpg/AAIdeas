package com.aaideas.krossvord.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One "word of the day": a hard word and a description to guess it from. */
@Serializable
data class DailyWord(val word: String, val clue: String, val source: String = "")

object DailyWords {
    const val MAX_ATTEMPTS = 3

    fun parse(text: String): List<DailyWord> = Json { ignoreUnknownKeys = true }.decodeFromString(text)

    /** Local day number (days since 1970-01-01 in the phone's time zone). Works on API 24. */
    fun dayNumber(millis: Long = System.currentTimeMillis(), zone: java.util.TimeZone = java.util.TimeZone.getDefault()): Long =
        Math.floorDiv(millis + zone.getOffset(millis), 86_400_000L)

    /**
     * Word for a day. The list is walked in a fixed shuffled order, so neighbouring days
     * don't get neighbouring list entries, and every word comes once per round.
     */
    fun forDay(words: List<DailyWord>, day: Long): DailyWord {
        val order = words.indices.shuffled(java.util.Random(2026L))
        return words[order[Math.floorMod(day, words.size.toLong()).toInt()]]
    }
}

/** State of today's guess, kept immutable so it is easy to test. */
data class DailyGuess(
    val word: DailyWord,
    val typed: String = "",
    val revealed: Set<Int> = emptySet(),
    val attempts: Int = 0,
    val solved: Boolean = false,
) {
    val letters: List<Char> = AzText.letters(word.word)
    val failed: Boolean get() = !solved && attempts >= DailyWords.MAX_ATTEMPTS
    val finished: Boolean get() = solved || failed

    /** Letters in the boxes: revealed ones are fixed, typed ones fill the rest in order. */
    fun boxes(): List<Char?> {
        val free = letters.indices.filter { it !in revealed }
        val out = MutableList<Char?>(letters.size) { i -> if (i in revealed) letters[i] else null }
        typed.forEachIndexed { k, ch -> free.getOrNull(k)?.let { out[it] = ch } }
        return out
    }

    private val freeCount get() = letters.size - revealed.size

    fun type(ch: Char): DailyGuess =
        if (finished || typed.length >= freeCount) this else AzText.normalizeLetter(ch)?.let { copy(typed = typed + it) } ?: this

    fun backspace(): DailyGuess = if (finished || typed.isEmpty()) this else copy(typed = typed.dropLast(1))

    /** Checks a full word. Right: solved. Wrong: one attempt used and the boxes clear. */
    fun submit(): DailyGuess {
        if (finished || typed.length < freeCount) return this
        val guess = boxes().joinToString("") { it.toString() }
        return if (guess == letters.joinToString("")) copy(solved = true)
        else copy(attempts = attempts + 1, typed = "")
    }

    /** Opens one more letter (a hint); never the last missing one. */
    fun reveal(random: java.util.Random = java.util.Random()): DailyGuess {
        val hidden = letters.indices.filter { it !in revealed }
        if (finished || hidden.size <= 1) return this
        return copy(revealed = revealed + hidden[random.nextInt(hidden.size)], typed = "")
    }
}
