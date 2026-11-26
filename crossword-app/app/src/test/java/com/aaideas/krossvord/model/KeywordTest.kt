package com.aaideas.krossvord.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordTest {
    // ALMA across, AD down sharing the A; key word "DAL" = D(2,1), A(1,1), L(1,2).
    private val alma = Entry(1, Direction.ACROSS, 1, 0, "ALMA", "x")
    private val ad = Entry(2, Direction.DOWN, 0, 1, "AD", "y")
    private val level = Level("t", 3, 5, 1, listOf(alma, ad), Keyword("DAL", listOf(listOf(2, 1), listOf(1, 1), listOf(1, 2))))

    @Test fun lettersAppearAsWordsAreSolved() {
        var s = GameState.start(Board(level))
        assertEquals(listOf(null, null, null), s.keywordLetters)
        s = s.submitWord(alma, "ALMA".toList()).first
        assertEquals(listOf(null, 'A', 'L'), s.keywordLetters)
        s = s.submitWord(ad, "D".toList()).first
        assertTrue(s.isKeywordKnown)
    }

    @Test fun earlyGuessEarnsBonusOnce() {
        val s = GameState.start(Board(level))
        assertFalse(s.guessKeyword("dam").second)
        val (guessed, ok) = s.guessKeyword(" dal ")
        assertTrue(ok)
        assertTrue(guessed.isKeywordKnown)
        assertFalse("no second bonus", guessed.guessKeyword("DAL").second)
    }

    @Test(expected = IllegalArgumentException::class)
    fun keywordMustMatchItsCells() {
        Board(level.copy(keyword = Keyword("LAD", listOf(listOf(2, 1), listOf(1, 1), listOf(1, 2)))))
    }

    @Test fun bundledLevelsHaveValidKeywords() {
        val pack = Pack.parse(javaClass.classLoader!!.getResource("packs/az-starter.json")!!.readText())
        assertTrue(pack.levels.all { it.keyword != null })
    }
}
