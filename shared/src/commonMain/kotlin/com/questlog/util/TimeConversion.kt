package com.questlog.util

/**
 * Pure, platform-independent conversion functions translating avoided screen-time
 * into in-game rewards. All functions here are unit-testable without any Android deps.
 *
 * Reward rates (base):
 *  XP  = 10 per minute saved
 *  Gold = 2 per minute saved
 *
 * Streak multiplier = 1.0 + (days * 0.10), capped at 3.0×
 *
 * Level thresholds use triangular progression:
 *  xpForLevel(n) = 100 * n * (n + 1) / 2
 */
object TimeConversion {

    private const val XP_PER_MINUTE = 10L
    private const val GOLD_PER_MINUTE = 2L
    private const val STREAK_STEP = 0.10f
    private const val MAX_MULTIPLIER = 3.0f

    /**
     * Calculates the streak multiplier for a given number of consecutive detox days.
     * Day 0 → 1.0×, Day 10 → 2.0×, Day 20+ → 3.0×
     */
    fun streakMultiplier(consecutiveDays: Int): Float =
        minOf(1.0f + consecutiveDays * STREAK_STEP, MAX_MULTIPLIER)

    /**
     * Total XP earned for [savedMs] milliseconds avoided, factoring in [streakMultiplier].
     */
    fun xpEarned(savedMs: Long, streakMultiplier: Float): Long {
        val minutes = savedMs / 60_000L
        return (minutes * XP_PER_MINUTE * streakMultiplier).toLong()
    }

    /**
     * Total gold earned for [savedMs] milliseconds avoided, factoring in [streakMultiplier].
     */
    fun goldEarned(savedMs: Long, streakMultiplier: Float): Long {
        val minutes = savedMs / 60_000L
        return (minutes * GOLD_PER_MINUTE * streakMultiplier).toLong()
    }

    /**
     * Returns the total cumulative XP required to reach level [n].
     * Level 1 starts at 0 XP, Level 2 starts at 100 XP, Level 3 starts at 300 XP, etc.
     */
    fun xpForLevel(n: Int): Long {
        require(n >= 1) { "Level must be >= 1" }
        if (n == 1) return 0L
        val prev = n - 1L
        return 100L * prev * (prev + 1) / 2
    }

    /**
     * Derives the current level from [totalXp], returning at least 1.
     */
    fun levelFromXp(totalXp: Long): Int {
        if (totalXp <= 0L) return 1
        var level = 1
        while (xpForLevel(level + 1) <= totalXp) level++
        return level
    }

    /**
     * Progress within the current level, as a 0f..1f fraction.
     */
    fun xpProgress(totalXp: Long): Float {
        if (totalXp <= 0L) return 0f
        val level = levelFromXp(totalXp)
        val start = xpForLevel(level)
        val end = xpForLevel(level + 1)
        val range = end - start
        return if (range <= 0L) 1f else ((totalXp - start).toFloat() / range).coerceIn(0f, 1f)
    }
}
