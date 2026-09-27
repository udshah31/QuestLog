package com.questlog.domain.model

import kotlinx.datetime.LocalDate

data class DaySaved(val date: LocalDate, val savedMs: Long, val isToday: Boolean)

/** The Progress screen's long view. [last7Days] is exactly 7, chronological, `[6]` is today. */
data class ProgressStats(
    val streakDays: Int,
    val reclaimedAllTimeMs: Long,
    val bestDayMs: Long,
    val appsGuarded: Int,
    val questsCleared: Int,
    val level: Int,
    val xp: Long,
    val xpToNextLevel: Long,
    val last7Days: List<DaySaved>,
)
