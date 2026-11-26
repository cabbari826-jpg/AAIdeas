package com.aaideas.krossvord.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RankTest {
    @Test fun ranksFollowXp() {
        assertEquals("Şagird", Ranks.of(0).title)
        assertEquals("Şagird", Ranks.of(99).title)
        assertEquals("Həvəskar", Ranks.of(100).title)
        assertEquals("Ustad", Ranks.of(10_000).title)
        assertNull(Ranks.next(10_000))
        assertEquals(listOf("rank_1", "rank_2"), Ranks.unlockedThemes(300))
    }

    @Test fun moreStarsMoreXp() {
        assertEquals(15, Ranks.levelXp(1))
        assertEquals(25, Ranks.levelXp(3))
    }
}
