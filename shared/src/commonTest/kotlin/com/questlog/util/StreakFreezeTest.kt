package com.questlog.util

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreakFreezeTest {

    private val today = LocalDate(2026, 8, 28)
    private fun daysAgo(n: Int) = today.minus(n, DateTimeUnit.DAY).toString()

    @Test
    fun `a charge that has never been used is available`() {
        assertTrue(StreakFreeze.isRechargedOn("", today))
    }

    @Test
    fun `an unparseable date is treated as available`() {
        assertTrue(StreakFreeze.isRechargedOn("not-a-date", today))
    }

    @Test
    fun `used today is not recharged`() {
        assertFalse(StreakFreeze.isRechargedOn(daysAgo(0), today))
    }

    @Test
    fun `used 6 days ago is not recharged`() {
        assertFalse(StreakFreeze.isRechargedOn(daysAgo(6), today))
    }

    @Test
    fun `used exactly 7 days ago is recharged`() {
        assertTrue(StreakFreeze.isRechargedOn(daysAgo(7), today))
    }
}
