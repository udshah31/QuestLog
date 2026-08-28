package com.questlog.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * The streak-freeze charge (a Pro perk) protects one missed detox day, then recharges
 * [COOLDOWN_DAYS] days after it is spent.
 */
object StreakFreeze {

    const val COOLDOWN_DAYS = 7

    /**
     * True if a freeze charge is available on [today]. [lastUsedKey] is the ISO date the
     * charge was last spent, or "" if it has never been used. Both corruption modes fail
     * open (favour the user): an unparseable value, and a future-dated value (the device
     * clock moved backward) are treated as available.
     */
    fun isRechargedOn(lastUsedKey: String, today: LocalDate): Boolean {
        if (lastUsedKey.isEmpty()) return true
        val lastUsed = runCatching { LocalDate.parse(lastUsedKey) }.getOrNull() ?: return true
        val elapsedDays = lastUsed.daysUntil(today)
        return elapsedDays < 0 || elapsedDays >= COOLDOWN_DAYS
    }
}
