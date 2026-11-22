package com.aaideas.krossvord.model

import kotlin.random.Random

/**
 * "Letter columns" input, anagram style: the letters still needed for the active word are
 * scattered over a 4-column board (two columns on the left, two on the right); the other
 * spots stay empty. Optional extra letters make it harder. The player taps letters in order
 * to build the word; when it is full it is checked as a whole.
 */
object WordBuilder {
    const val COLUMNS = 4
    const val EXTRA_LETTERS = 3
    private const val DECOY_POOL = "AAƏƏIİLRNMEDKSYTUBQOŞÇZGÜXHVÖPCĞ"

    /** Board spots: at least 12 (3 per column), a multiple of 4. */
    fun spotCount(letters: Int): Int {
        val n = maxOf(12, letters)
        return (n + COLUMNS - 1) / COLUMNS * COLUMNS
    }

    /** Board spots in reading order; null = empty spot. Same word and [shuffle] give the same board. */
    fun letters(state: GameState, entry: Entry, shuffle: Int = 0, extra: Boolean = false): List<Char?> {
        val open = state.openCells(entry)
        if (open.isEmpty()) return emptyList()
        val rnd = Random(state.board.level.id.hashCode() * 17 + entry.id * 7919 + open.size * 131 + shuffle)
        val needed = open.map { state.board.solution.getValue(it) }
        val decoys = if (extra) List(EXTRA_LETTERS) { DECOY_POOL[rnd.nextInt(DECOY_POOL.length)] } else emptyList()
        val all = needed + decoys
        val spots = arrayOfNulls<Char>(spotCount(all.size))
        spots.indices.shuffled(rnd).take(all.size).forEachIndexed { i, pos -> spots[pos] = all[i] }
        return spots.toList()
    }

    /** Column [c] (0..3) gets every 4th spot, so letters are spread over both sides. */
    fun column(spots: List<Char?>, c: Int): List<IndexedValue<Char?>> =
        spots.withIndex().filter { it.index % COLUMNS == c }
}
