package com.aaideas.krossvord.model

import kotlin.random.Random

/**
 * "Letter columns" input: the open letters of the active word plus decoys are scattered
 * over four columns (two on the left, two on the right). The player taps letters in order
 * to build the word; when it is full it is checked as a whole.
 */
object WordBuilder {
    const val COLUMNS = 4
    private const val DECOY_POOL = "AAƏƏIİLRNMEDKSYTUBQOŞÇZGÜXHVÖPCĞ"

    /** Board size: at least 12 letters (3 per column), at least 4 decoys, a multiple of 4, so columns stay even. */
    fun letterCount(open: Int): Int {
        val n = maxOf(12, open + 4)
        return (n + COLUMNS - 1) / COLUMNS * COLUMNS
    }

    fun letters(state: GameState, entry: Entry, shuffle: Int = 0): List<Char> {
        val open = state.openCells(entry)
        if (open.isEmpty()) return emptyList()
        val rnd = Random(state.board.level.id.hashCode() * 17 + entry.id * 7919 + open.size * 131 + shuffle)
        val needed = open.map { state.board.solution.getValue(it) }
        val decoys = List(letterCount(open.size) - needed.size) { DECOY_POOL[rnd.nextInt(DECOY_POOL.length)] }
        return (needed + decoys).shuffled(rnd)
    }

    /** Column [c] (0..3) gets every 4th letter, so letters are spread over both sides. */
    fun column(letters: List<Char>, c: Int): List<IndexedValue<Char>> =
        letters.withIndex().filter { it.index % COLUMNS == c }
}
