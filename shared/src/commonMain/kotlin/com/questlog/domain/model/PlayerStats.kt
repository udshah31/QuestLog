package com.questlog.domain.model

/** Aggregated snapshot for the dashboard. */
data class PlayerStats(
    val level: Int,
    val xp: Long,
    val xpToNextLevel: Long,
    val gold: Long,
    val gems: Long,
    val consecutiveDetoxDays: Int,
    val streakMultiplier: Float,
    val todaySavedMs: Long,
    val streakFreezeReady: Boolean = false,
    /** All-time saved screen-time (ms): finalised past days plus today's award so far. */
    val lifetimeSavedMs: Long = 0L,
)
