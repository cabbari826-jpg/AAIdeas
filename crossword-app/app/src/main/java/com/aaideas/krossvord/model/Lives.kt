package com.aaideas.krossvord.model

/**
 * Lives shared by all levels. A wrong word costs one. New players start with a bonus of
 * [START]; after that lives come back on their own up to [CAP], one every [REGEN_MS].
 * A rewarded ad refills to [CAP]; the word of the day refills, or adds one to the reserve
 * when already full. Lives above [CAP] (bonus, reserve) are kept until spent.
 *
 * [count] is the lives at [since]; [since] is when the regeneration timer last started.
 */
data class Lives(val count: Int, val since: Long) {
    /** Lives now, counting the ones that came back while the player was away. */
    fun at(now: Long): Lives {
        if (count >= CAP) return Lives(count, now)
        val back = ((now - since) / REGEN_MS).toInt().coerceAtLeast(0)
        if (back == 0) return this
        val n = (count + back).coerceAtMost(CAP)
        return if (n >= CAP) Lives(n, now) else Lives(n, since + back * REGEN_MS)
    }

    fun lose(now: Long): Lives {
        val cur = at(now)
        if (cur.count <= 0) return cur
        // The timer starts when the player drops below the cap, not before.
        return if (cur.count >= CAP) Lives(cur.count - 1, now) else Lives(cur.count - 1, cur.since)
    }

    fun refill(now: Long): Lives = at(now).let { if (it.count >= CAP) it else Lives(CAP, now) }

    /** Word-of-the-day reward: back to full, or one more in reserve when already full. */
    fun reward(now: Long): Lives = at(now).let {
        if (it.count < CAP) Lives(CAP, now) else Lives((it.count + 1).coerceAtMost(MAX), now)
    }

    /** A gift of [n] lives (birthday); above the cap they stay in reserve. */
    fun gift(now: Long, n: Int = 1): Lives = at(now).let {
        val c = (it.count + n).coerceAtMost(MAX)
        if (c >= CAP) Lives(c, now) else Lives(c, it.since)
    }

    /** Milliseconds until the next life comes back, or null when full. */
    fun msToNext(now: Long): Long? = at(now).let { if (it.count >= CAP) null else REGEN_MS - (now - it.since) }

    companion object {
        const val CAP = 3
        const val START = 5
        const val MAX = 9
        const val REGEN_MS = 20 * 60 * 1000L
        fun newPlayer(now: Long) = Lives(START, now)
    }
}
