package com.aaideas.krossvord.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyExtrasTest {
    @Test fun everyProverbHasOneHiddenWordAndFourDistinctOptions() {
        Proverbs.RAW.forEach { assertEquals(it, 1, it.count { c -> c == '[' }) }
        for (day in 0L..400L) {
            val o = Proverbs.options(day)
            assertEquals(4, o.distinct().size)
            assertTrue(Proverbs.forDay(day).answer in o)
        }
    }

    @Test fun tasksAreThreeDifferentKinds() {
        for (day in 0L..400L) {
            val t = DailyTasks.forDay(day, hasMini = false)
            assertEquals(3, t.map { it.kind }.distinct().size)
            assertFalse(t.any { it.kind == TaskKind.MINI })
        }
    }

    @Test fun streakRunAndSevenDayGift() {
        val days = (100L..106L).toSet()
        assertEquals(7, Streak.run(days, 106))
        assertEquals(7, Streak.run(days, 107))   // yesterday still counts until today is played
        assertEquals(0, Streak.run(days, 108))
        assertTrue(Streak.giftDue(days, 106, Long.MIN_VALUE))
        assertFalse(Streak.giftDue(days, 106, 106))
        assertFalse(Streak.giftDue(days - 106, 105, Long.MIN_VALUE))
    }

    @Test fun calendarStartsOnMondayAndEndsThisWeek() {
        val today = java.time.LocalDate.of(2026, 11, 22).toEpochDay() // Sunday
        val c = Streak.calendar(today)
        assertEquals(4, c.size)
        assertEquals(java.time.DayOfWeek.MONDAY, java.time.LocalDate.ofEpochDay(c[0][0]).dayOfWeek)
        assertEquals(today, c.last().last())
    }

    @Test fun factsAreShortEnoughForTheCard() {
        Facts.ALL.forEach { assertTrue(it, it.length <= 110) }
        assertEquals(Facts.ALL[0], Facts.at(Facts.ALL.size))
    }
}
