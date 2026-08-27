package com.questlog.domain.model

/** Emitted by the detox monitor on each periodic update. */
data class DetoxMetrics(
    val timeSavedMs: Long,
    val xpEarned: Long,
    val goldEarned: Long,
    val currentLevel: Int,
    val xpProgress: Float,          // 0f..1f within the current level
    val consecutiveDetoxDays: Int,
    val streakMultiplier: Float,
)
