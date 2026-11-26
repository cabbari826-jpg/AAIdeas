package com.aaideas.krossvord.model

import kotlin.random.Random

/** A letter tile under the grid. [used] tiles are already placed in the word. */
data class Tile(val letter: Char, val used: Boolean)

/**
 * Tile input: the letters still needed for the active word, shuffled, plus a few decoys.
 * Letters already fixed (revealed, or part of a solved crossing word) get no tile.
 * The result is deterministic for the same word and [shuffle], so tiles don't jump around while typing.
 */
object LetterTiles {
    /** Frequent Azerbaijani letters make the best decoys: they look plausible. */
    private const val DECOY_POOL = "AAƏƏIİLRNMEDKSYTUBQOŞÇZGÜXHVÖPCĞ"

    fun decoyCount(openCells: Int): Int = when {
        openCells <= 3 -> 2
        openCells <= 5 -> 3
        else -> 4
    }

    fun tilesFor(state: GameState, entry: Entry, shuffle: Int = 0): List<Tile> {
        val open = entry.cells.filter { !state.isLocked(it) }
        if (open.isEmpty()) return emptyList()
        val rnd = Random(state.board.level.id.hashCode() * 31 + entry.id * 1009 + shuffle)
        val needed = open.map { state.board.solution.getValue(it) }
        val decoys = List(decoyCount(open.size)) { DECOY_POOL[rnd.nextInt(DECOY_POOL.length)] }
        val letters = (needed + decoys).shuffled(rnd)

        // Mark one tile as used for every letter the player has already put into an open cell.
        val placed = open.mapNotNull { state.typed[it] }.groupingBy { it }.eachCount().toMutableMap()
        return letters.map { ch ->
            val left = placed[ch] ?: 0
            if (left > 0) { placed[ch] = left - 1; Tile(ch, used = true) } else Tile(ch, used = false)
        }
    }
}
