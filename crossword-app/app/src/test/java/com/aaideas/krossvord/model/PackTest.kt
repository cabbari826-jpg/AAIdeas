package com.aaideas.krossvord.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PackTest {
    private fun bundled(): Pack {
        val text = javaClass.classLoader!!.getResource("packs/az-starter.json")!!.readText(Charsets.UTF_8)
        return Pack.parse(text)
    }

    @Test fun everyBundledPackIsValid() {
        val files = java.io.File("src/main/assets/packs").listFiles()!!.filter { it.name.endsWith(".json") }
        assertTrue(files.size >= 5)
        files.forEach { check(Pack.parse(it.readText())) }
    }

    @Test fun bundledPackIsValid() = check(bundled())

    private fun check(pack: Pack) {
        assertEquals("az", pack.language)
        assertTrue(pack.levels.isNotEmpty())
        val ids = pack.levels.map { it.id }
        assertEquals("level ids must be unique", ids.size, ids.toSet().size)
        for (level in pack.levels) {
            val board = Board(level)
            // Every letter cell belongs to at least one word, every clue cell holds at most 2 clues.
            assertTrue(board.solution.keys.all { board.entriesAt[it].orEmpty().isNotEmpty() })
            assertTrue(board.cluesAt.values.all { it.size <= 2 })
            // The easiest clue (the default one) must fit into a cell.
            level.entries.forEach { assertTrue("clue too long: ${it.clue}", it.clue.length <= 16) }
            // No word twice in one level, and no single-letter answers.
            assertEquals(level.entries.size, level.entries.map { it.answer }.toSet().size)
            assertTrue(level.entries.all { it.answer.length >= 2 })
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun conflictingCrossingIsRejected() {
        Board(
            Level(
                "bad", 3, 3, 1,
                listOf(
                    Entry(1, Direction.ACROSS, 1, 0, "AT", "x"),
                    Entry(2, Direction.DOWN, 0, 1, "OD", "y"), // crosses (1,1): 'A' vs 'O'
                ),
            )
        )
    }
}
