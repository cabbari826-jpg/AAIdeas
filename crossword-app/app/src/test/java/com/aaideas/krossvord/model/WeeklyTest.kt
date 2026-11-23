package com.aaideas.krossvord.model

import com.aaideas.krossvord.data.DownloadedPacks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeklyTest {
    @Test fun oneSurpriseOpensEachWeek() {
        val mon = LocalDate.of(2026, 11, 23)
        assertEquals(0, Weekly.weekOf(mon))
        assertEquals(0, Weekly.weekOf(mon.plusDays(6)))
        assertEquals(1, Weekly.weekOf(mon.plusDays(7)))
        assertTrue(Weekly.weekOf(mon.minusDays(1)) < 0)
        assertTrue(Weekly.isOpen(0, mon))
        assertFalse(Weekly.isOpen(1, mon.plusDays(6)))
        assertTrue(Weekly.isOpen(1, mon.plusDays(7)))
        assertTrue(Weekly.isWeekly("az-hefte") && Weekly.isWeekly("hefte-2") && !Weekly.isWeekly("az-starter"))
    }

    private fun pack(id: String) = Pack(1, id, "az", AzText.NORMALIZATION_VERSION, 1, id, emptyList())

    @Test fun downloadedPackGoesRightAfterThePackItContinues() {
        val bundled = listOf(pack("az-starter"), pack("az-cografiya"), pack("az-tarix"))
        val merged = DownloadedPacks.merge(
            bundled,
            listOf(pack("az-starter-plus"), pack("hefte-2"), pack("az-starter-plus2"), pack("az-tarix")),
            mapOf("az-starter-plus" to "az-starter", "az-starter-plus2" to "az-starter", "hefte-2" to null),
        )
        assertEquals(
            listOf("az-starter", "az-starter-plus", "az-starter-plus2", "az-cografiya", "az-tarix", "hefte-2"),
            merged.map { it.packId },
        )
    }
}
