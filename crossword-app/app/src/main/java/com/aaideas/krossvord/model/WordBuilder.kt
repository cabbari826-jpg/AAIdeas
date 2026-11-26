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

    /** Board spots: at least 16 (4 per column), a multiple of 4. */
    fun spotCount(letters: Int): Int {
        val n = maxOf(16, letters)
        return (n + COLUMNS - 1) / COLUMNS * COLUMNS
    }

    /** Board spots in reading order; null = empty spot. Same word and [shuffle] give the same board. */
    fun letters(state: GameState, entry: Entry, shuffle: Int = 0, extra: Boolean = false): List<Char?> {
        val open = state.openCells(entry)
        if (open.isEmpty()) return emptyList()
        val rnd = Random(state.board.level.id.hashCode() * 17 + entry.id * 7919 + open.size * 131 + shuffle)
        val needed = open.map { state.board.solution.getValue(it) }
        val neighbours = neighbourLetters(state, entry, targetLetters(needed.size) - needed.size, rnd)
        val decoys = if (extra) List(EXTRA_LETTERS) { DECOY_POOL[rnd.nextInt(DECOY_POOL.length)] } else emptyList()
        val all = needed + neighbours + decoys
        val spots = arrayOfNulls<Char>(spotCount(all.size))
        spots.indices.shuffled(rnd).take(all.size).forEachIndexed { i, pos -> spots[pos] = all[i] }
        return spots.toList()
    }

    /** How many letters to show for a word with [needed] open cells: never a near-certain guess. */
    fun targetLetters(needed: Int): Int = minOf(16, maxOf(needed + 10, 14)).coerceAtLeast(needed)

    /**
     * Instead of random decoys: letters of other unsolved words of the level, so every letter
     * on the board belongs to some real answer. Words are taken from the whole level in random order,
     * so the extra letters don't point at the crossing words.
     */
    fun neighbourLetters(state: GameState, entry: Entry, count: Int, rnd: Random = Random(0)): List<Char> {
        if (count <= 0) return emptyList()
        val own = entry.cells.toSet()
        val order = state.board.level.entries
            .filter { it != entry && !state.isEntrySolved(it) }
            .shuffled(rnd)
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
