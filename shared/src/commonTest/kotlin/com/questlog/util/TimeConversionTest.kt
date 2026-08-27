package com.questlog.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimeConversionTest {

    // ── streakMultiplier ──────────────────────────────────────────────────────

    @Test
    fun `streakMultiplier is 1_0 on day 0`() {
        assertEquals(1.0f, TimeConversion.streakMultiplier(0))
    }

    @Test
    fun `streakMultiplier grows by 0_1 per day`() {
        assertEquals(1.5f, TimeConversion.streakMultiplier(5), absoluteTolerance = 0.001f)
        assertEquals(2.0f, TimeConversion.streakMultiplier(10), absoluteTolerance = 0.001f)
    }

    @Test
    fun `streakMultiplier is capped at 3_0`() {
        assertEquals(3.0f, TimeConversion.streakMultiplier(20))
        assertEquals(3.0f, TimeConversion.streakMultiplier(100))
    }

    // ── xpEarned ─────────────────────────────────────────────────────────────

    @Test
    fun `xpEarned returns 0 for 0ms saved`() {
        assertEquals(0L, TimeConversion.xpEarned(0L, 1.0f))
    }

    @Test
    fun `xpEarned returns 10 XP per minute at 1_0x multiplier`() {
        val thirtyMinutes = 30 * 60_000L
        assertEquals(300L, TimeConversion.xpEarned(thirtyMinutes, 1.0f))
    }

    @Test
    fun `xpEarned scales with streak multiplier`() {
        val sixtyMinutes = 60 * 60_000L
        val base = TimeConversion.xpEarned(sixtyMinutes, 1.0f) // 600 XP
        val doubled = TimeConversion.xpEarned(sixtyMinutes, 2.0f)
        assertEquals(base * 2, doubled)
    }

    @Test
    fun `xpEarned does not overflow with large savedMs`() {
        // 1000 hours at 3× multiplier should not throw
        val bigMs = 1000L * 60 * 60_000L
        val result = TimeConversion.xpEarned(bigMs, 3.0f)
        assertTrue(result > 0L)
    }

    // ── goldEarned ────────────────────────────────────────────────────────────

    @Test
    fun `goldEarned returns 0 for 0ms saved`() {
        assertEquals(0L, TimeConversion.goldEarned(0L, 1.0f))
    }

    @Test
    fun `goldEarned returns 2 gold per minute at 1_0x multiplier`() {
        val thirtyMinutes = 30 * 60_000L
        assertEquals(60L, TimeConversion.goldEarned(thirtyMinutes, 1.0f))
    }

    // ── xpForLevel ────────────────────────────────────────────────────────────

    @Test
    fun `xpForLevel is monotonically increasing`() {
        for (n in 1..50) {
            assertTrue(TimeConversion.xpForLevel(n + 1) > TimeConversion.xpForLevel(n))
        }
    }

    @Test
    fun `level 1 requires 0 XP`() {
        assertEquals(0L, TimeConversion.xpForLevel(1))
    }

    @Test
    fun `level 2 requires 100 XP total`() {
        assertEquals(100L, TimeConversion.xpForLevel(2))
    }

    @Test
    fun `level 3 requires 300 XP total`() {
        assertEquals(300L, TimeConversion.xpForLevel(3))
    }

    // ── levelFromXp ───────────────────────────────────────────────────────────

    @Test
    fun `levelFromXp returns 1 for 0 XP`() {
        assertEquals(1, TimeConversion.levelFromXp(0L))
    }

    @Test
    fun `levelFromXp promotes correctly at threshold`() {
        assertEquals(1, TimeConversion.levelFromXp(99L))
        assertEquals(2, TimeConversion.levelFromXp(100L))
        assertEquals(2, TimeConversion.levelFromXp(299L))
        assertEquals(3, TimeConversion.levelFromXp(300L))
    }

    // ── xpProgress ────────────────────────────────────────────────────────────

    @Test
    fun `xpProgress is 0f at level start`() {
        // Level 2 starts at 100 XP
        assertEquals(0.0f, TimeConversion.xpProgress(100L), absoluteTolerance = 0.001f)
    }

    @Test
    fun `xpProgress is within 0f to 1f`() {
        for (xp in listOf(0L, 50L, 100L, 500L, 10_000L)) {
            val progress = TimeConversion.xpProgress(xp)
            assertTrue(progress in 0f..1f, "xpProgress($xp) = $progress out of range")
        }
    }
}

// extension for more readable float assertions
private fun assertEquals(expected: Float, actual: Float, absoluteTolerance: Float) {
    assertTrue(
        kotlin.math.abs(expected - actual) <= absoluteTolerance,
        "Expected $expected ± $absoluteTolerance but was $actual"
    )
}
