package com.questlog.util

import kotlin.test.Test
import kotlin.test.assertEquals

private const val MIN = 60_000L

class DetoxBudgetTest {

    @Test
    fun `nothing is saved before any of the day has elapsed`() {
        assertEquals(0L, DetoxBudget.savedTimeMs(budgetMs = 90 * MIN, elapsedMs = 0L, flaggedForegroundMs = 0L))
    }

    @Test
    fun `saved time is the elapsed portion of the budget not spent on flagged apps`() {
        // 40 min into the day, 10 min of it on flagged apps -> 30 min saved
        assertEquals(30 * MIN, DetoxBudget.savedTimeMs(budgetMs = 90 * MIN, elapsedMs = 40 * MIN, flaggedForegroundMs = 10 * MIN))
    }

    @Test
    fun `saved time is capped at the budget once enough of the day has elapsed`() {
        // Whole day elapsed, no flagged use -> capped at the 90 min budget, not 24h
        assertEquals(90 * MIN, DetoxBudget.savedTimeMs(budgetMs = 90 * MIN, elapsedMs = 24 * 60 * MIN, flaggedForegroundMs = 0L))
    }

    @Test
    fun `heavy flagged use drives saved time to zero, never negative`() {
        assertEquals(0L, DetoxBudget.savedTimeMs(budgetMs = 90 * MIN, elapsedMs = 24 * 60 * MIN, flaggedForegroundMs = 200 * MIN))
    }

    @Test
    fun `chargeableMs is zero when usage is within the allowance`() {
        assertEquals(0L, DetoxBudget.chargeableMs(usageMs = 10 * 60_000L, allowanceMs = 30 * 60_000L))
        assertEquals(0L, DetoxBudget.chargeableMs(usageMs = 30 * 60_000L, allowanceMs = 30 * 60_000L))
    }

    @Test
    fun `chargeableMs is the overage when usage exceeds the allowance`() {
        assertEquals(5 * 60_000L, DetoxBudget.chargeableMs(usageMs = 35 * 60_000L, allowanceMs = 30 * 60_000L))
    }

    @Test
    fun `chargeableMs with a zero allowance charges all usage`() {
        assertEquals(42 * 60_000L, DetoxBudget.chargeableMs(usageMs = 42 * 60_000L, allowanceMs = 0L))
    }

    @Test
    fun `chargeableMs never goes negative`() {
        assertEquals(0L, DetoxBudget.chargeableMs(usageMs = 0L, allowanceMs = 30 * 60_000L))
    }
}
