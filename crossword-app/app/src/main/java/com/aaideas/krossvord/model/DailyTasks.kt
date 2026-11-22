package com.aaideas.krossvord.model

/** Things the player did today that tasks count. */
enum class TaskKind { LEVELS, PERFECT, WORDS, WORD_OF_DAY, PROVERB, MINI }

data class DailyTask(val kind: TaskKind, val target: Int, val title: String)

/**
 * Three small tasks a day, picked by the day number; each gives XP, all three give a life.
 * Gentle targets: a parent playing ten minutes a day should finish them.
 */
object DailyTasks {
    const val TASK_XP = 20
    private val POOL = listOf(
        DailyTask(TaskKind.LEVELS, 1, "1 səviyyə həll et"),
        DailyTask(TaskKind.LEVELS, 2, "2 səviyyə həll et"),
        DailyTask(TaskKind.PERFECT, 1, "Bir səviyyəni səhvsiz həll et"),
        DailyTask(TaskKind.WORDS, 10, "10 söz tap"),
        DailyTask(TaskKind.WORDS, 20, "20 söz tap"),
        DailyTask(TaskKind.WORD_OF_DAY, 1, "Günün sözünü tap"),
        DailyTask(TaskKind.PROVERB, 1, "Günün atalar sözünü tamamla"),
        DailyTask(TaskKind.MINI, 1, "Mini krossvordu həll et"),
    )

    /** Three tasks of different kinds for [day]; [hasMini] false leaves the mini task out. */
    fun forDay(day: Long, hasMini: Boolean = true): List<DailyTask> {
        val pool = POOL.filter { hasMini || it.kind != TaskKind.MINI }.shuffled(java.util.Random(day * 7919 + 13))
        return pool.distinctBy { it.kind }.take(3)
    }
}

/**
 * Days the player played, for the streak calendar. Every 7 days in a row give a gift once.
 * Days are epoch days in the phone's time zone.
 */
object Streak {
    const val GIFT_EVERY = 7

    /** Days in a row ending today (or yesterday, so the run isn't lost before today's play). */
    fun run(days: Set<Long>, today: Long): Int {
        var d = if (today in days) today else today - 1
        var n = 0
        while (d in days) { n++; d-- }
        return n
    }

    /** True when today's play completes a run of 7, 14, ... days. */
    fun giftDue(days: Set<Long>, today: Long, lastGiftDay: Long): Boolean {
        val r = run(days, today)
        return today in days && r > 0 && r % GIFT_EVERY == 0 && lastGiftDay != today
    }

    /** Monday-first weeks covering the last [weeks] weeks, for the calendar grid. */
    fun calendar(today: Long, weeks: Int = 4): List<List<Long>> {
        val dow = java.time.LocalDate.ofEpochDay(today).dayOfWeek.value - 1 // Monday = 0
        val start = today - dow - (weeks - 1) * 7L
        return (0 until weeks).map { w -> (0 until 7).map { start + w * 7L + it } }
    }
}
