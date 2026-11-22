package com.aaideas.krossvord.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClueTierTest {
    private val od = Entry(1, Direction.ACROSS, 1, 0, "OD", "Alov", clues = listOf("Yanğın səbəbi", "Ocaqda yanır", "Alov"))
    private val board = Board(Level("t", 3, 3, 1, listOf(od)))

    @Test fun startsHardestAndEasesStepByStep() {
        var s = GameState.start(board)
        assertEquals("Yanğın səbəbi", s.clueText(od))
        s = s.easeClue(od)
        assertEquals("Ocaqda yanır", s.clueText(od))
        s = s.easeClue(od)
        assertEquals("Alov", s.clueText(od))
        assertFalse(s.canEaseClue(od))
        assertEquals(s, s.easeClue(od))
    }

    @Test fun singleClueStillWorks() {
        val e = Entry(1, Direction.ACROSS, 1, 0, "OD", "Alov")
        val s = GameState.start(Board(Level("t", 3, 3, 1, listOf(e))))
        assertEquals("Alov", s.clueText(e))
        assertFalse(s.canEaseClue(e))
    }

    @Test fun answersDoNotRepeatAcrossTheBundledPack() {
        val pack = Pack.parse(javaClass.classLoader!!.getResource("packs/az-starter.json")!!.readText())
        val all = pack.levels.flatMap { l -> l.entries.map { it.answer } }
        val dupes = all.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        assertTrue("repeated answers: $dupes", dupes.isEmpty())
    }

    @Test fun everyBundledEntryHasThreeShortClueLevels() {
        val pack = Pack.parse(javaClass.classLoader!!.getResource("packs/az-starter.json")!!.readText())
        for (e in pack.levels.flatMap { it.entries }) {
            assertEquals("${e.answer} clue levels", 3, e.clueLevels.size)
            e.clueLevels.forEach { assertTrue("too long: $it", it.length <= 16) }
        }
    }
}
