package com.aaideas.krossvord.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppIconTest {
    @Test fun holidaySeasons() {
        assertEquals(AppIcon.NOVRUZ, AppIcon.holidayFor(3, 20))
        assertEquals(AppIcon.YENI_IL, AppIcon.holidayFor(12, 31))
        assertEquals(AppIcon.YENI_IL, AppIcon.holidayFor(1, 7))
        assertNull(AppIcon.holidayFor(1, 8))
        assertNull(AppIcon.holidayFor(6, 1))
    }

    @Test fun premiumIconNeedsPremium() {
        assertEquals(AppIcon.XALCA, AppIcon.target(AppIcon.XALCA, holidays = false, isPremium = true, month = 6, day = 1))
        assertEquals(AppIcon.STANDARD, AppIcon.target(AppIcon.XALCA, holidays = false, isPremium = false, month = 6, day = 1))
    }

    @Test fun holidayIconOnlyWhenTurnedOn() {
        assertEquals(AppIcon.NOVRUZ, AppIcon.target(AppIcon.STANDARD, holidays = true, isPremium = false, month = 3, day = 21))
        assertEquals(AppIcon.STANDARD, AppIcon.target(AppIcon.STANDARD, holidays = false, isPremium = false, month = 3, day = 21))
        // After the season the player's own choice comes back.
        assertEquals(AppIcon.GECE, AppIcon.target(AppIcon.GECE, holidays = true, isPremium = true, month = 4, day = 6))
    }
}

class DecorSeasonTest {
    private fun d(y: Int, m: Int, day: Int) = java.time.LocalDate.of(y, m, day)

    @Test fun seasons() {
        assertEquals(DecorSeason.YENI_IL, DecorSeason.on(d(2026, 12, 31)))
        assertEquals(DecorSeason.NOVRUZ, DecorSeason.on(d(2027, 3, 20)))
        assertEquals(DecorSeason.RAMAZAN, DecorSeason.on(d(2027, 2, 20)))
        assertEquals(DecorSeason.QURBAN, DecorSeason.on(d(2027, 5, 17)))
        assertNull(DecorSeason.on(d(2027, 7, 1)))
        assertEquals(DecorSeason.BAYRAM, DecorSeason.on(d(2027, 5, 28)))
        assertEquals(DecorSeason.ZEFER, DecorSeason.on(d(2026, 11, 8)))
        assertEquals(DecorSeason.ANIM, DecorSeason.on(d(2027, 9, 27)))
    }

    @Test fun moonFillsThroughRamazan() {
        assertEquals(1, DecorSeason.ramazanDay(d(2027, 2, 8)))
        assertEquals(30, DecorSeason.ramazanDay(d(2027, 3, 10))) // bayram day: full moon
        assertNull(DecorSeason.ramazanDay(d(2027, 3, 13)))
    }
}
