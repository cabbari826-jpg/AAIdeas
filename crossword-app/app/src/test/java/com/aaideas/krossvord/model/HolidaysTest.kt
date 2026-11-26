package com.aaideas.krossvord.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HolidaysTest {
    @Test fun greetsOnTheDay() {
        assertEquals("yeni_il", Holidays.greeting(LocalDate.of(2027, 1, 1))?.id)
        assertEquals("yeni_il", Holidays.greeting(LocalDate.of(2026, 12, 30))?.id) // two days before
        assertEquals("novruz", Holidays.greeting(LocalDate.of(2027, 3, 22))?.id)
        assertEquals("musteqillik", Holidays.greeting(LocalDate.of(2026, 5, 28))?.id)
        assertNull(Holidays.greeting(LocalDate.of(2026, 7, 14)))
    }

    @Test fun newYearPackOpensAroundNewYearOnly() {
        assertTrue(Holidays.packOpen("az-yeniil", LocalDate.of(2026, 12, 20)))
        assertTrue(Holidays.packOpen("az-yeniil", LocalDate.of(2027, 1, 14)))
        assertFalse(Holidays.packOpen("az-yeniil", LocalDate.of(2027, 2, 1)))
        assertTrue(Holidays.packOpen("az-starter", LocalDate.of(2027, 2, 1)))
    }

    @Test fun idsAreUnique() = assertEquals(Holidays.ALL.size, Holidays.ALL.map { it.id }.toSet().size)
}
