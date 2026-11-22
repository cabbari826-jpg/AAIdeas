package com.aaideas.krossvord.model

/**
 * Pure, immutable puzzle state: what the player typed, the selection, and checks.
 * No Android dependencies, so it is covered by plain JVM unit tests.
 */
data class GameState(
    val board: Board,
    val typed: Map<Cell, Char> = emptyMap(),
    val revealed: Set<Cell> = emptySet(),
    val selectedCell: Cell? = null,
    val direction: Direction = Direction.ACROSS,
) {
    val activeEntry: Entry?
        get() {
            val cell = selectedCell ?: return null
            val entries = board.entriesAt[cell] ?: return null
            return entries.firstOrNull { it.direction == direction } ?: entries.first()
        }

    fun isEntryFilled(e: Entry) = e.cells.all { typed[it] != null }
    fun isEntrySolved(e: Entry) = e.cells.all { typed[it] == board.solution[it] }
    fun isEntryWrong(e: Entry) = isEntryFilled(e) && !isEntrySolved(e)

    /** A cell is locked when it is revealed or belongs to a solved word. */
    fun isLocked(c: Cell) = c in revealed || board.entriesAt[c].orEmpty().any { isEntrySolved(it) }

    val isComplete: Boolean get() = board.solution.all { (c, ch) -> typed[c] == ch }

    val solvedCount: Int get() = board.level.entries.count { isEntrySolved(it) }

    /** Tap on a letter cell: select it; a second tap on the same cell switches direction. */
    fun tapCell(c: Cell): GameState {
        val entries = board.entriesAt[c] ?: return this
        if (c == selectedCell && entries.size > 1) {
            return copy(direction = if (direction == Direction.ACROSS) Direction.DOWN else Direction.ACROSS)
        }
        val dir = if (entries.any { it.direction == direction }) direction else entries.first().direction
        return copy(selectedCell = c, direction = dir)
    }

    /** Tap on a clue cell: select its word (cycling if the cell holds two clues). */
    fun tapClue(c: Cell): GameState {
        val clues = board.cluesAt[c] ?: return this
        val current = activeEntry
        val next = if (current in clues && clues.size > 1) clues.first { it != current } else clues.first()
        return selectEntry(next)
    }

    fun selectEntry(e: Entry): GameState {
        val first = e.cells.firstOrNull { typed[it] == null && !isLocked(it) } ?: e.cells.first()
        return copy(selectedCell = first, direction = e.direction)
    }

    fun type(letter: Char): GameState {
        val ch = AzText.normalizeLetter(letter) ?: return this
        val cell = selectedCell ?: return this
        val entry = activeEntry ?: return this
        val target = if (isLocked(cell)) nextFree(entry, cell) ?: return this else cell
        val newState = copy(typed = typed + (target to ch))
        val next = newState.nextFree(entry, target)
        return newState.copy(selectedCell = next ?: target)
    }

    /** Clears the current cell, or, if it is empty, the previous one. Locked cells are kept. */
    fun backspace(): GameState {
        val cell = selectedCell ?: return this
        val entry = activeEntry ?: return this
        if (typed[cell] != null && !isLocked(cell)) return copy(typed = typed - cell)
        val idx = entry.cells.indexOf(cell)
        for (i in idx - 1 downTo 0) {
            val p = entry.cells[i]
            if (!isLocked(p)) return copy(typed = typed - p, selectedCell = p)
        }
        return this
    }

    /** Free correction, never behind an ad: clears the unlocked letters of the active word. */
    fun clearActiveEntry(): GameState {
        val entry = activeEntry ?: return this
        val keep = entry.cells.filter { isLocked(it) }.toSet()
        return copy(typed = typed - (entry.cells.toSet() - keep), selectedCell = entry.cells.firstOrNull { it !in keep } ?: selectedCell)
    }

    /** Hint: reveals the correct letter in the selected cell (or the first wrong/empty cell of the word). */
    fun revealLetter(): GameState {
        val entry = activeEntry ?: return this
        val target = selectedCell?.takeIf { typed[it] != board.solution[it] }
            ?: entry.cells.firstOrNull { typed[it] != board.solution[it] }
            ?: return this
        val s = copy(typed = typed + (target to board.solution.getValue(target)), revealed = revealed + target)
        return s.copy(selectedCell = s.nextFree(entry, target) ?: target)
    }

    private fun nextFree(entry: Entry, from: Cell): Cell? {
        val idx = entry.cells.indexOf(from)
        return entry.cells.drop(idx + 1).firstOrNull { !isLocked(it) }
    }

    companion object {
        fun start(board: Board): GameState {
            val first = board.level.entries.first()
            return GameState(board).selectEntry(first)
        }
    }
}
