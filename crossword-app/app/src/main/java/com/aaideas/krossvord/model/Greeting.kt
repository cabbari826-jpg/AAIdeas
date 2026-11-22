package com.aaideas.krossvord.model

import java.time.LocalDate
import java.time.MonthDay

/** Welcome texts and the player's birthday (day and month only, kept on the phone). */
object Greeting {
    val MONTHS = listOf(
        "Yanvar", "Fevral", "Mart", "Aprel", "May", "İyun",
        "İyul", "Avqust", "Sentyabr", "Oktyabr", "Noyabr", "Dekabr",
    )

    /** "Sabahınız xeyir, Ana!" by the hour of the day; without a name just the greeting. */
    fun hello(hour: Int, name: String?): String {
        val g = when (hour) {
            in 5..11 -> "Sabahınız xeyir"
            in 12..17 -> "Günortanız xeyir"
            in 18..22 -> "Axşamınız xeyir"
            else -> "Salam"
        }
        val n = name?.trim().orEmpty()
        return if (n.isEmpty()) "$g!" else "$g, $n!"
    }

    /** Stored as "MM-dd"; null when not given or broken. */
    fun parse(s: String?): MonthDay? = s?.let { runCatching { MonthDay.parse("--$it") }.getOrNull() }
    fun format(md: MonthDay): String = "%02d-%02d".format(md.monthValue, md.dayOfMonth)

    /** 29 February birthdays are on 28 February in other years. */
    fun isBirthday(day: LocalDate, md: MonthDay?): Boolean {
        if (md == null) return false
        if (md.monthValue == 2 && md.dayOfMonth == 29 && !day.isLeapYear) return day.monthValue == 2 && day.dayOfMonth == 28
        return MonthDay.from(day) == md
    }

    fun label(md: MonthDay) = "${md.dayOfMonth} ${MONTHS[md.monthValue - 1]}"
}
