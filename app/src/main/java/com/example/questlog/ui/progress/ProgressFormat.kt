package com.example.questlog.ui.progress

import com.example.questlog.ui.format.formatReclaimed
import com.questlog.domain.model.DaySaved
import kotlinx.datetime.DayOfWeek

fun dayInitial(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "M"
    DayOfWeek.TUESDAY -> "T"
    DayOfWeek.WEDNESDAY -> "W"
    DayOfWeek.THURSDAY -> "T"
    DayOfWeek.FRIDAY -> "F"
    DayOfWeek.SATURDAY -> "S"
    DayOfWeek.SUNDAY -> "S"
    else -> ""
}

/** Show the empty state until at least two of the seven days have any saved time. */
fun showsEmptyWeek(days: List<DaySaved>): Boolean = days.count { it.savedMs > 0 } < 2

/** A bar's height as a fraction of the week's best day (0 for an all-zero week). */
fun barFraction(savedMs: Long, days: List<DaySaved>): Float =
    savedMs.toFloat() / days.maxOf { it.savedMs }.coerceAtLeast(1L)

/** XP still needed for the next level; [xpToNextLevel] is that level's total threshold. */
fun xpToGo(xp: Long, xpToNextLevel: Long): Long = (xpToNextLevel - xp).coerceAtLeast(0L)

/** "1h 30m" / "45m". */
fun reclaimedLine(ms: Long): String = formatReclaimed(ms).let { "${it.hours ?: ""} ${it.minutes}".trim() }
