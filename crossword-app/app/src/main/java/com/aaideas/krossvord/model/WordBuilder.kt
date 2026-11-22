package com.aaideas.krossvord.model

import kotlin.random.Random

/**
 * "Letter columns" input: the letters still needed for the active word, mixed with letters of
 * other unsolved words of the level, scattered over a 4-column board (two columns on the left,
 * two on the right). Optional random extra letters make it harder. The player taps letters in order
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
        val neighbours = neighbourLetters(state, entry, targetLetters(needed.size) - needed.size)
        val decoys = if (extra) List(EXTRA_LETTERS) { DECOY_POOL[rnd.nextInt(DECOY_POOL.length)] } else emptyList()
        val all = needed + neighbours + decoys
        val spots = arrayOfNulls<Char>(spotCount(all.size))
        spots.indices.shuffled(rnd).take(all.size).forEachIndexed { i, pos -> spots[pos] = all[i] }
        return spots.toList()
    }

    /** How many letters to show for a word with [needed] open cells: never a near-certain guess. */
    fun targetLetters(needed: Int): Int = minOf(12, maxOf(needed + 5, 9)).coerceAtLeast(needed)

    /**
     * Instead of random decoys: letters of other unsolved words of the level, so every letter
     * on the board belongs to some real answer. Crossing words first, then the next words in order.
     */
    fun neighbourLetters(state: GameState, entry: Entry, count: Int): List<Char> {
        if (count <= 0) return emptyList()
        val own = entry.cells.toSet()
        val all = state.board.level.entries
        val i = all.indexOf(entry)
        val crossing = entry.cells.flatMap { state.board.entriesAt[it].orEmpty() }.filter { it != entry }
        val order = (crossing + all.drop(i + 1) + all.take(i)).distinct()
            .filter { it != entry && !state.isEntrySolved(it) }
        val out = mutableListOf<Char>()
        for (e in order) {
            for (c in state.openCells(e)) {
                if (c in own) continue
                out += state.board.solution.getValue(c)
                if (out.size == count) return out
            }
        }
        return out
    }

    /** Column [c] (0..3) gets every 4th spot, so letters are spread over both sides. */
    fun column(spots: List<Char?>, c: Int): List<IndexedValue<Char?>> =
        spots.withIndex().filter { it.index % COLUMNS == c }
}
