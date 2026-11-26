package com.aaideas.krossvord.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LivesTest {
    private val m = Lives.REGEN_MS

    @Test fun newPlayersGetABonusThatDoesNotComeBack() {
        var l = Lives.newPlayer(0)
        repeat(3) { l = l.lose(0) }
        assertEquals(2, l.count)
        // Only up to 3 come back.
        assertEquals(3, l.at(10 * m).count)
    }

    @Test fun livesComeBackOneByOne() {
        var l = Lives(3, 0).lose(0).lose(0).lose(0)
        assertEquals(0, l.count)
        assertEquals(m, l.msToNext(0))
        assertEquals(1, l.at(m).count)
        assertEquals(1, l.at(2 * m - 1).count)
        assertEquals(2, l.at(2 * m).count)
        assertEquals(3, l.at(5 * m).count)
        assertNull(l.msToNext(5 * m))
    }

    @Test fun timerStartsWhenDroppingBelowTheCap() {
        val l = Lives(4, 0).lose(5 * m)   // 4 -> 3, still full
        assertEquals(3, l.count)
        val l2 = l.lose(6 * m)            // 3 -> 2, timer starts now
        assertEquals(2, l2.at(6 * m + m - 1).count)
        assertEquals(3, l2.at(7 * m).count)
    }

    @Test fun cannotGoBelowZero() = assertEquals(0, Lives(0, 0).lose(0).count)

    @Test fun adRefillsAndDailyWordRewards() {
        assertEquals(3, Lives(0, 0).refill(1).count)
        assertEquals(3, Lives(1, 0).reward(1).count)
        assertEquals(4, Lives(3, 0).reward(1).count)
        assertEquals(Lives.MAX, Lives(Lives.MAX, 0).reward(1).count)
    }
}
