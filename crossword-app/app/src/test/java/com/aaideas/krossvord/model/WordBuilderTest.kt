package com.aaideas.krossvord.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordBuilderTest {
    private val alma = Entry(1, Direction.ACROSS, 1, 0, "ALMA", "Quba meyvəsi")
    private val ad = Entry(2, Direction.DOWN, 0, 1, "AD", "İsim")
    private val board = Board(Level("t", 3, 5, 1, listOf(alma, ad)))

    @Test fun boardIsAnAnagramOfTheWord() {
        val s = GameState.start(board)
        val spots = WordBuilder.letters(s, alma)
        assertEquals(12, spots.size)
        assertEquals("AALM", spots.filterNotNull().sorted().joinToString(""))
        val cols = (0 until 4).map { WordBuilder.column(spots, it).size }
        assertEquals(listOf(3, 3, 3, 3), cols)
    }

    @Test fun extraLettersAreOptional() {
        val s = GameState.start(board)
        val spots = WordBuilder.letters(s, alma, extra = true)
        assertEquals(4 + WordBuilder.EXTRA_LETTERS, spots.filterNotNull().size)
    }

    @Test fun spotCountGrowsWithLongWords() {
        assertEquals(12, WordBuilder.spotCount(2))
        assertEquals(12, WordBuilder.spotCount(12))
        assertEquals(16, WordBuilder.spotCount(13))
    }

    @Test fun lockedLettersAreNotOnTheBoard() {
        val s = GameState.start(board).submitWord(ad, "AD".toList()).first
        assertEquals("ALM", WordBuilder.letters(s, alma).filterNotNull().sorted().joinToString(""))
    }

    @Test fun rightWordFillsGridWrongWordChangesNothing() {
        val s = GameState.start(board)
        val (same, wrong) = s.submitWord(alma, "ALMO".toList())
        assertFalse(wrong)
        assertEquals(s, same)
        val (done, ok) = s.submitWord(alma, "ALMA".toList())
        assertTrue(ok)
        assertTrue(done.isEntrySolved(alma))
    }

    @Test fun lockedCrossingLetterIsSkipped() {
        val s = GameState.start(board).submitWord(ad, "AD".toList()).first
        assertEquals(3, s.openCells(alma).size)
        val (done, ok) = s.submitWord(alma, "LMA".toList())
        assertTrue(ok)
        assertTrue(done.isComplete)
    }

    @Test fun onlyFirstClueStartsOpen() {
        val s = GameState.start(board)
        assertTrue(s.isClueOpen(alma))
        assertFalse(s.isClueOpen(ad))
        assertTrue(s.openClue(ad).isClueOpen(ad))
        assertTrue("solved words show their clue", s.submitWord(ad, "AD".toList()).first.isClueOpen(ad))
    }
}
