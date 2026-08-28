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
}
