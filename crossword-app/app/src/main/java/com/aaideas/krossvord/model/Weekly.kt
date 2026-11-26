package com.aaideas.krossvord.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * "Həftənin sürprizi": small packs (about 3 levels) whose id contains "hefte". The first one
 * opens in the week of [START], the next one a week later, and so on. A new weekly pack comes
 * either with an update or as a downloadable pack from Açar Söz Studio; the game fetches those
 * by itself once a day when there is internet, so the surprise is ready offline.
 */
object Weekly {
    /** Monday of the first surprise week. */
    val START: LocalDate = LocalDate.of(2026, 11, 23)

    fun isWeekly(packId: String) = "hefte" in packId

    /** 0 in the first week, 1 in the next, ...; negative before [START]. */
    fun weekOf(day: LocalDate): Int = Math.floorDiv(ChronoUnit.DAYS.between(START, day), 7L).toInt()

    /** Is the weekly pack at [position] (0 = first weekly pack in pack order) open on [day]? */
    fun isOpen(position: Int, day: LocalDate): Boolean = position <= weekOf(day)

    /** Monday when the weekly pack at [position] opens. */
    fun opensOn(position: Int): LocalDate = START.plusWeeks(position.toLong())
}
