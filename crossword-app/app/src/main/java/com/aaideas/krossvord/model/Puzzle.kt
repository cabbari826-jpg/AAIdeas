package com.aaideas.krossvord.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class Direction { ACROSS, DOWN }

/** One answer. Its clue sits in the clue cell (clueRow, clueCol); letters start in the next cell. */
@Serializable
data class Entry(
    val id: Int,
    val direction: Direction,
    val clueRow: Int,
    val clueCol: Int,
    val answer: String,
    val clue: String,
    val source: String = "",
    val status: String = "candidate",
    /** Optional clue levels, hardest first. The first is shown; easier ones are opened as hints. */
    val clues: List<String> = emptyList(),
) {
    val clueLevels: List<String> get() = clues.ifEmpty { listOf(clue) }

    val letters: List<Char> by lazy { AzText.letters(answer) }

    val cells: List<Cell> by lazy {
        letters.indices.map { i ->
            if (direction == Direction.ACROSS) Cell(clueRow, clueCol + 1 + i) else Cell(clueRow + 1 + i, clueCol)
        }
    }
}

/**
 * Scandinavian "key word": highlighted letter cells whose letters, in order, spell a bonus word.
 * [cells] are [row, col] pairs.
 */
@Serializable
data class Keyword(
    val word: String,
    val cells: List<List<Int>>,
    val source: String = "",
) {
    val cellList: List<Cell> by lazy { cells.map { Cell(it[0], it[1]) } }
}

@Serializable
data class Level(
    val id: String,
    val rows: Int,
    val cols: Int,
    val difficulty: Int = 1,
    val entries: List<Entry>,
    val keyword: Keyword? = null,
)

@Serializable
data class Pack(
    val formatVersion: Int,
    val packId: String,
    val language: String,
    val normalization: String,
    val contentVersion: Int,
    val title: String,
    val levels: List<Level>,
) {
    companion object {
        const val SUPPORTED_FORMAT = 1
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): Pack {
            val pack = json.decodeFromString(serializer(), text)
            require(pack.formatVersion <= SUPPORTED_FORMAT) { "Unsupported pack format ${pack.formatVersion}" }
            require(pack.normalization == AzText.NORMALIZATION_VERSION) { "Unknown normalization ${pack.normalization}" }
            pack.levels.forEach { Board(it) } // validates every level
            return pack
        }
    }
}

data class Cell(val row: Int, val col: Int)

/**
 * The static layout of a Scandinavian level: letter cells with their solution,
 * and clue cells holding up to one ACROSS (→) and one DOWN (↓) clue.
 */
class Board(val level: Level) {
    val rows = level.rows
    val cols = level.cols
    val solution: Map<Cell, Char>
    val entriesAt: Map<Cell, List<Entry>>
    val cluesAt: Map<Cell, List<Entry>>

    init {
        val sol = HashMap<Cell, Char>()
        val at = HashMap<Cell, MutableList<Entry>>()
        val clues = HashMap<Cell, MutableList<Entry>>()
        val answers = HashSet<String>()
        for (e in level.entries) {
            require(e.letters.size >= 2) { "${level.id}: entry ${e.id} is shorter than 2 letters" }
            require(answers.add(e.answer)) { "${level.id}: duplicate answer ${e.answer}" }
            val clueCell = Cell(e.clueRow, e.clueCol)
            require(inside(clueCell)) { "${level.id}: clue of entry ${e.id} is outside the grid" }
            val sameDir = clues.getOrPut(clueCell) { mutableListOf() }
            require(sameDir.none { it.direction == e.direction }) { "${level.id}: two ${e.direction} clues in $clueCell" }
            sameDir += e
            e.cells.forEachIndexed { i, cell ->
                require(inside(cell)) { "${level.id}: entry ${e.id} runs outside the grid" }
                val prev = sol.put(cell, e.letters[i])
                require(prev == null || prev == e.letters[i]) {
                    "${level.id}: conflict at $cell between '${prev}' and '${e.letters[i]}' (${e.answer})"
                }
                val list = at.getOrPut(cell) { mutableListOf() }
                require(list.none { it.direction == e.direction }) { "${level.id}: two ${e.direction} entries overlap at $cell" }
                list += e
            }
        }
        for (cell in clues.keys) require(cell !in sol) { "${level.id}: $cell is both a clue and a letter" }
        level.keyword?.let { k ->
            val cells = k.cellList
            require(k.cells.all { it.size == 2 }) { "${level.id}: keyword cells must be [row, col]" }
            require(cells.toSet().size == cells.size) { "${level.id}: keyword uses a cell twice" }
            require(cells.all { it in sol }) { "${level.id}: keyword cell is not a letter cell" }
            val spelled = cells.map { sol.getValue(it) }
            require(spelled == AzText.letters(k.word)) { "${level.id}: keyword cells spell ${spelled.joinToString("")}, not ${k.word}" }
        }
        solution = sol
        entriesAt = at
        cluesAt = clues
    }

    private fun inside(c: Cell) = c.row in 0 until rows && c.col in 0 until cols

    fun isLetter(c: Cell) = c in solution
}
