package com.aaideas.krossvord.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DailyWordTest {
    private val word = DailyWord("ŞƏFƏQ", "Dan yeri qızartısı")

    private fun typeAll(g: DailyGuess, s: String) = s.fold(g) { acc, c -> acc.type(c) }

    @Test fun rightGuessSolves() {
        val g = typeAll(DailyGuess(word), "şəfəq").submit()
        assertTrue(g.solved)
        assertTrue(g.finished)
    }

    @Test fun threeWrongGuessesFail() {
        var g = DailyGuess(word)
        repeat(DailyWords.MAX_ATTEMPTS) { g = typeAll(g, "ABCDE").submit() }
        assertTrue(g.failed)
        assertEquals(g, g.type('A'))
    }

    @Test fun revealKeepsOneLetterHidden() {
        var g = DailyGuess(word)
        repeat(10) { g = g.reveal(java.util.Random(it.toLong())) }
        assertEquals(word.word.length - 1, g.revealed.size)
        val solved = g.type(word.word[g.letters.indices.first { it !in g.revealed }]).submit()
        assertTrue(solved.solved)
    }

    @Test fun shortGuessIsNotChecked() {
        val g = typeAll(DailyGuess(word), "ŞƏ").submit()
        assertEquals(0, g.attempts)
        assertFalse(g.finished)
    }

    @Test fun bundledWordsAreValidAndNotInPacks() {
        val words = DailyWords.parse(File("src/main/assets/word_of_day.json").readText())
        assertTrue(words.size >= 30)
        assertEquals(words.size, words.map { it.word }.toSet().size)
        val day = DailyWords.dayNumber()
        assertEquals(DailyWords.forDay(words, day), DailyWords.forDay(words, day))
        val packWords = File("src/main/assets/packs").listFiles()!!.flatMap { f ->
            Regex("\"answer\":\\s*\"([^\"]+)\"").findAll(f.readText()).map { it.groupValues[1] }.toList()
        }.toSet()
        words.forEach { assertFalse("${it.word} is also in a pack", it.word in packWords) }
    }
}
