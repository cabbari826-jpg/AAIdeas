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

    /** Every bundled pack: the main one and the themes. */
    private val packs: List<Pack> = java.io.File("src/main/assets/packs").listFiles()!!
        .filter { it.name.endsWith(".json") }.sortedBy { it.name }
        .map { Pack.parse(it.readText()) }

    @Test fun answersDoNotRepeatAcrossAllPacks() {
        assertTrue(packs.size >= 5)
        val all = packs.flatMap { p -> p.levels.flatMap { l -> l.entries.map { it.answer } } }
        val dupes = all.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        assertTrue("repeated answers: $dupes", dupes.isEmpty())
    }

    @Test fun cluesAndKeywordsDoNotRepeatAcrossAllPacks() {
        val levels = packs.flatMap { it.levels }
        val clues = levels.flatMap { l -> l.entries.flatMap { it.clueLevels.distinct() } }
        val dupes = clues.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        assertTrue("repeated clues: $dupes", dupes.isEmpty())
        val answers = levels.flatMap { l -> l.entries.map { it.answer } }.toSet()
        val keywords = levels.mapNotNull { it.keyword?.word }
        assertEquals("repeated key words", keywords.size, keywords.toSet().size)
        assertTrue("key word is also an answer", keywords.none { it in answers })
    }

    @Test fun everyEntryHasThreeShortClueLevels() {
        for (p in packs) for (e in p.levels.flatMap { it.entries }) {
            assertEquals("${e.answer} clue levels", 3, e.clueLevels.size)
            e.clueLevels.forEachIndexed { i, c ->
                // Song lines (music pack, hardest level) may be longer: the grid shows ♪ for them.
                val max = if (i == 0 && p.packId == "az-musiqi") 40 else 16
                assertTrue("too long: $c", c.length <= max)
            }
        }
    }

    @Test fun everyThemedPackHasAtLeastTenLevels() {
        // Holiday packs are short (open for a few weeks only), but not tiny.
        packs.filter { it.packId != "az-starter" }.forEach {
            val min = if (Holidays.holidayFor(it.packId) != null) 6 else 10
            assertTrue(it.packId, it.levels.size >= min)
        }
    }
}
