package com.aaideas.krossvord.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedalsTest {
    private val pack = Pack(1, "az-test", "az", "az", 1, "Test", emptyList())

    @Test fun nothingAtStart() = assertTrue(Medals.earnedIds(listOf(pack), MedalStats()).isEmpty())

    @Test fun medalsFollowProgress() {
        val ids = Medals.earnedIds(listOf(pack), MedalStats(packsDone = setOf("az-test"), levelsDone = 12, perfectLevels = 3, bestPerfectRun = 3, dailyBestStreak = 7, wordsFound = 120))
        assertEquals(
            setOf("first_level", "levels_10", "perfect_1", "perfect_run_3", "daily_3", "daily_7", "words_100", "pack_az-test"),
            ids,
        )
    }

    @Test fun idsAreUnique() {
        val all = Medals.all(listOf(pack))
        assertEquals(all.size, all.map { it.id }.toSet().size)
    }
}
