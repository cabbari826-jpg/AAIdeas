package com.aaideas.krossvord.model

import java.time.LocalDate
import java.time.MonthDay

/**
 * Azerbaijani holidays: a greeting with a short fact on the home screen while one lasts, and
 * for some a holiday pack that is open only around those days. [showFrom]/[showTo] are days
 * before/after the date. Facts are short and checkable; the owner reviews them.
 */
data class Holiday(
    val id: String,
    val icon: String,
    val title: String,
    val greeting: String,
    val fact: String,
    /** Fixed date each year, or null for holidays that move (see [moving]). */
    val date: MonthDay?,
    val moving: List<LocalDate> = emptyList(),
    val showFrom: Int = 0,
    val showTo: Int = 0,
    val packId: String? = null,
    val packFrom: Int = 0,
    val packTo: Int = 0,
) {
    private fun dates(year: Int): List<LocalDate> =
        if (date != null) listOf(date.atYear(year - 1), date.atYear(year), date.atYear(year + 1)) else moving

    private fun within(day: LocalDate, before: Int, after: Int) =
        dates(day.year).any { !day.isBefore(it.minusDays(before.toLong())) && !day.isAfter(it.plusDays(after.toLong())) }

    fun greets(day: LocalDate) = within(day, showFrom, showTo)
    fun packOpen(day: LocalDate) = packId != null && within(day, packFrom, packTo)
}

@Suppress("MagicNumber")
object Holidays {
    private fun md(m: Int, d: Int) = MonthDay.of(m, d)

    val ALL = listOf(
        // 31 December: Solidarity Day first, the New Year greeting around it.
        Holiday(
            "hemrey", "🤝", "Həmrəylik günü", "Dünya azərbaycanlılarının həmrəylik günü mübarək!",
            "Bu bayram 1991-ci ildən qeyd olunur.", md(12, 31),
            packId = "az-hemreylik", packFrom = 7, packTo = 7,
        ),
        Holiday(
            "yeni_il", "🎄", "Yeni il", "Yeni iliniz mübarək! Arzularınız çin olsun.",
            "Azərbaycanda 1 və 2 yanvar qeyri-iş günləridir.",
            md(1, 1), showFrom = 2, showTo = 2, packId = "az-yeniil", packFrom = 14, packTo = 14,
        ),
        Holiday(
            "qadinlar", "💐", "8 Mart", "Qadınlar günü mübarək!",
            "8 Mart Beynəlxalq Qadınlar Günüdür.", md(3, 8), showFrom = 1,
            packId = "az-qadinlar", packFrom = 7, packTo = 7,
        ),
        Holiday(
            "novruz", "🌱", "Novruz bayramı", "Novruz bayramınız mübarək! Bahar evinizə xeyir-bərəkət gətirsin.",
            "Novruz 2009-cu ildə UNESCO-nun qeyri-maddi mədəni irs siyahısına daxil edilib. Bayramdan əvvəl dörd çərşənbə qeyd olunur: Su, Od, Yel və Torpaq.",
            md(3, 20), showFrom = 1, showTo = 5,
            packId = "az-novruz", packFrom = 20, packTo = 10,
        ),
        Holiday(
            "qelebe", "🕊", "9 May", "Faşizm üzərində Qələbə günü mübarək!",
            "1945-ci ilin mayında İkinci Dünya müharibəsi Avropada başa çatdı.", md(5, 9),
            packId = "az-qelebe", packFrom = 7, packTo = 7,
        ),
        Holiday(
            "musteqillik", "🇦🇿", "Müstəqillik günü", "Müstəqillik günü mübarək!",
            "28 may 1918-ci ildə Azərbaycan Xalq Cümhuriyyəti elan olundu: müsəlman Şərqində ilk demokratik respublika.", md(5, 28),
            packId = "az-musteqillik", packFrom = 7, packTo = 7,
        ),
        Holiday(
            "qurtulus", "🇦🇿", "Milli Qurtuluş günü", "Milli Qurtuluş günü mübarək!",
            "15 iyun Azərbaycanda rəsmi bayram günüdür.", md(6, 15),
            packId = "az-qurtulus", packFrom = 7, packTo = 7,
        ),
        Holiday(
            "ordu", "🎖", "Silahlı Qüvvələr günü", "Silahlı Qüvvələr günü mübarək!",
            "Azərbaycan Milli Ordusu 26 iyun 1918-ci ildə yaradılıb.", md(6, 26),
            packId = "az-ordu", packFrom = 7, packTo = 7,
        ),
        Holiday(
            "zefer", "🏅", "Zəfər günü", "Zəfər günü mübarək!",
            "8 noyabr 2021-ci ildən Zəfər günü kimi qeyd olunur.", md(11, 8),
            packId = "az-zefer", packFrom = 7, packTo = 7,
        ),
        Holiday(
            "bayraq", "🇦🇿", "Dövlət Bayrağı günü", "Dövlət Bayrağı günü mübarək!",
            "Üçrəngli bayraq 9 noyabr 1918-ci ildə qəbul edilib. Mavi türkçülüyü, qırmızı müasirliyi, yaşıl islamı bildirir.", md(11, 9),
            packId = "az-bayraq", packFrom = 7, packTo = 7,
        ),
        Holiday(
            "konstitusiya", "📜", "Konstitusiya günü", "Konstitusiya günü mübarək!",
            "Müstəqil Azərbaycanın Konstitusiyası 12 noyabr 1995-ci ildə referendumla qəbul edilib.", md(11, 12),
        ),
        Holiday(
            "dircelis", "🔥", "Milli Dirçəliş günü", "Milli Dirçəliş günü mübarək!",
            "17 noyabr 1988-ci ildə Bakıda Azadlıq meydanında xalq mitinqləri başladı.", md(11, 17),
        ),
        // Moving holidays: the dates are the expected first day and are announced officially each year.
        Holiday(
            "ramazan", "🌙", "Ramazan bayramı", "Ramazan bayramınız mübarək! Orucunuz qəbul olsun.",
            "Ramazan bayramı oruc ayının sonunu bildirir; Azərbaycanda iki gün qeyri-iş günüdür.",
            null, listOf(LocalDate.of(2026, 3, 20), LocalDate.of(2027, 3, 10), LocalDate.of(2028, 2, 27)), showTo = 1,
            packId = "az-ramazan", packFrom = 7, packTo = 7,
        ),
        Holiday(
            "qurban", "🌙", "Qurban bayramı", "Qurban bayramınız mübarək!",
            "Qurban bayramı həcc mövsümünə təsadüf edir; Azərbaycanda iki gün qeyri-iş günüdür.",
            null, listOf(LocalDate.of(2026, 5, 27), LocalDate.of(2027, 5, 17), LocalDate.of(2028, 5, 6)), showTo = 1,
            packId = "az-qurban", packFrom = 7, packTo = 7,
        ),
    )

    /** The holiday to greet on [day], if any. */
    fun greeting(day: LocalDate): Holiday? = ALL.firstOrNull { it.greets(day) }

    /** Is this holiday pack open on [day]? Packs that aren't holiday packs are always open. */
    fun packOpen(packId: String, day: LocalDate): Boolean {
        val h = ALL.firstOrNull { it.packId == packId } ?: return true
        return h.packOpen(day)
    }

    fun holidayFor(packId: String): Holiday? = ALL.firstOrNull { it.packId == packId }
}
