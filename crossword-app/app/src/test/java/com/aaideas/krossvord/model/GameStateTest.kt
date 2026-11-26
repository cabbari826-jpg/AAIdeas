package com.aaideas.krossvord.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tiny 3x3 board:
 *   #  #   #
 *   #  A   T      ACROSS "AT" from clue (1,0)
 *   #  D   #      DOWN  "AD" from clue (0,1)
 */
class GameStateTest {
    private val at = Entry(1, Direction.ACROSS, 1, 0, "AT", "Minik heyvanı")
    private val ad = Entry(2, Direction.DOWN, 0, 1, "AD", "İsim")
    private val board = Board(Level("t", 3, 3, 1, listOf(at, ad)))

    @Test fun typingFillsWordAndAdvances() {
        var s = GameState.start(board)
        assertEquals(at, s.activeEntry)
        s = s.type('a')
        assertEquals(Cell(1, 2), s.selectedCell)
        s = s.type('t')
        assertTrue(s.isEntrySolved(at))
        assertFalse(s.isComplete)
    }

    @Test fun crossingLetterIsShared() {
        var s = GameState.start(board).type('A').type('T')
        s = s.tapClue(Cell(0, 1))
        assertEquals(ad, s.activeEntry)
        assertEquals("first free cell skips the shared A", Cell(2, 1), s.selectedCell)
        s = s.type('D')
        assertTrue(s.isComplete)
    }

    @Test fun wrongWordCanBeCorrectedForFree() {
        var s = GameState.start(board).type('O').type('T')
        assertTrue(s.isEntryWrong(at))
        s = s.clearActiveEntry()
        assertNull(s.typed[Cell(1, 1)])
        assertNull(s.typed[Cell(1, 2)])
        s = s.type('A').type('T')
        assertTrue(s.isEntrySolved(at))
    }

    @Test fun solvedLettersAreLocked() {
        var s = GameState.start(board).type('A').type('T')
        s = s.tapCell(Cell(1, 1)).backspace()
        assertEquals('A', s.typed[Cell(1, 1)])
    }

    @Test fun tappingSameCellSwitchesDirection() {
        var s = GameState.start(board) // (1,1) selected, ACROSS
        assertEquals(Cell(1, 1), s.selectedCell)
        s = s.tapCell(Cell(1, 1))
        assertEquals(Direction.DOWN, s.direction)
        s = s.tapCell(Cell(1, 1))
        assertEquals(Direction.ACROSS, s.direction)
    }

    @Test fun hintRevealsCorrectLetter() {
        var s = GameState.start(board).revealLetter()
        assertEquals('A', s.typed[Cell(1, 1)])
        assertTrue(Cell(1, 1) in s.revealed)
        assertEquals(Cell(1, 2), s.selectedCell)
    }

    @Test fun backspaceGoesBack() {
        var s = GameState.start(board).type('A')
        s = s.backspace()
        assertNull(s.typed[Cell(1, 1)])
        assertEquals(Cell(1, 1), s.selectedCell)
    }
}
