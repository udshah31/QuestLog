package com.questlog.util

/**
 * Pure "saved time" math for the detox reward.
 *
 * A day grants at most [DEFAULT_DAILY_BUDGET_MS] of saved time, earned only as the day
 * actually elapses. Of the elapsed portion of the budget, every millisecond spent on a
 * flagged app is subtracted. The result never goes negative.
 */
object DetoxBudget {

    const val DEFAULT_DAILY_BUDGET_MS = 90 * 60_000L

    fun savedTimeMs(budgetMs: Long, elapsedMs: Long, flaggedForegroundMs: Long): Long =
        (minOf(budgetMs, elapsedMs) - flaggedForegroundMs).coerceAtLeast(0L)

    /**
     * The part of [usageMs] that counts against the player: everything beyond
     * [allowanceMs]. An allowance of 0 charges the full usage (fully-blocked app).
     */
    fun chargeableMs(usageMs: Long, allowanceMs: Long): Long =
        (usageMs - allowanceMs).coerceAtLeast(0L)
}
