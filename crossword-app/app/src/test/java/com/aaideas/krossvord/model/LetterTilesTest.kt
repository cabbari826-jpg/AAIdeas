package com.aaideas.krossvord.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LetterTilesTest {
    private val at = Entry(1, Direction.ACROSS, 1, 0, "ALMA", "x")
    private val down = Entry(2, Direction.DOWN, 0, 1, "AD", "y")
    private val board = Board(Level("t", 3, 5, 1, listOf(at, down)))

    private fun letters(tiles: List<Tile>) = tiles.map { it.letter }

    @Test fun containsAllNeededLettersPlusDecoys() {
        val s = GameState.start(board)
        val tiles = LetterTiles.tilesFor(s, at)
        assertEquals(4 + LetterTiles.decoyCount(4), tiles.size)
        val counts = letters(tiles).groupingBy { it }.eachCount()
        "ALMA".groupingBy { it }.eachCount().forEach { (ch, n) -> assertTrue((counts[ch] ?: 0) >= n) }
        assertTrue(tiles.none { it.used })
    }

    @Test fun stableUntilShuffled() {
        val s = GameState.start(board)
        assertEquals(LetterTiles.tilesFor(s, at), LetterTiles.tilesFor(s, at))
        val other = (1..5).map { LetterTiles.tilesFor(s, at, shuffle = it) }
        assertTrue(other.any { letters(it) != letters(LetterTiles.tilesFor(s, at)) })
    }

    @Test fun typedLettersMarkTilesUsed() {
        val s = GameState.start(board).type('A').type('L')
        val tiles = LetterTiles.tilesFor(s, at)
        assertEquals(2, tiles.count { it.used })
        assertEquals(letters(LetterTiles.tilesFor(GameState.start(board), at)), letters(tiles))
    }

    @Test fun lockedCrossingLetterHasNoTile() {
        // Solve AD first: its A is shared with ALMA and becomes locked.
        val s = GameState.start(board).selectEntry(down).type('A').type('D')
        val tiles = LetterTiles.tilesFor(s, at)
        assertEquals(3 + LetterTiles.decoyCount(3), tiles.size)
    }

    @Test fun solvedWordHasNoTiles() {
        val s = GameState.start(board).type('A').type('L').type('M').type('A')
        assertTrue(LetterTiles.tilesFor(s, at).isEmpty())
    }
}
