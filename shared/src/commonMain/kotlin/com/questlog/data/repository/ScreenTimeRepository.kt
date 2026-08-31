package com.questlog.data.repository

import com.questlog.data.local.dao.ScreenTimeDao
import com.questlog.data.local.entity.ScreenTimeRecord
import com.questlog.domain.model.AppUsage
import com.questlog.domain.platform.ScreenTimeTracker
import com.questlog.util.DetoxBudget
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

open class ScreenTimeRepository(
    private val dao: ScreenTimeDao,
    private val tracker: ScreenTimeTracker,
    private val dailyBudgetMs: Long = DetoxBudget.DEFAULT_DAILY_BUDGET_MS,
) {
    private fun today(): String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return "${now.year}-${now.monthNumber.toString().padStart(2,'0')}-${now.dayOfMonth.toString().padStart(2,'0')}"
    }

    /**
     * Records each flagged app's foreground time for today and returns the day's "saved
     * time" so far: of the elapsed portion of [dailyBudgetMs], the part not spent on
     * flagged apps (see [DetoxBudget]).
     *
     * Raw per-app foreground time is always persisted. [allowances] (package -> daily
     * allowance ms) only affects the reward input: per app, just the usage beyond its
     * allowance is charged (see [DetoxBudget.chargeableMs]). The default empty map
     * charges every flagged millisecond.
     */
    open suspend fun fetchAndPersistToday(
        flaggedPackages: Set<String>,
        startOfDayMs: Long,
        allowances: Map<String, Long> = emptyMap(),
    ): Long {
        val endMs = Clock.System.now().toEpochMilliseconds()
        val flagged: List<AppUsage> = tracker.getUsageForPeriod(startOfDayMs, endMs)
            .filter { it.packageName in flaggedPackages }

        for (usage in flagged) {
            dao.upsert(ScreenTimeRecord(usage.packageName, today(), usage.totalForegroundMs))
        }

        val flaggedForegroundMs = flagged.sumOf { usage ->
            DetoxBudget.chargeableMs(usage.totalForegroundMs, allowances[usage.packageName] ?: 0L)
        }
        return DetoxBudget.savedTimeMs(
            budgetMs = dailyBudgetMs,
            elapsedMs = endMs - startOfDayMs,
            flaggedForegroundMs = flaggedForegroundMs,
        )
    }

    /** Total flagged-app foreground milliseconds recorded for [date]. */
    open suspend fun totalForegroundMs(date: String): Long = dao.totalForegroundMsForDate(date)

    /** Foreground milliseconds for a single [packageName] on [date]. */
    open suspend fun foregroundMsForPackageOnDate(packageName: String, date: String): Long =
        dao.foregroundMsForPackageOnDate(packageName, date)

    /** Live-queries the tracker for combined [flaggedPackages] foreground time in [startMs, endMs). */
    open suspend fun flaggedForegroundInWindow(
        startMs: Long,
        endMs: Long,
        flaggedPackages: Set<String>,
    ): Long =
        tracker.getUsageForPeriod(startMs, endMs)
            .filter { it.packageName in flaggedPackages }
            .sumOf { it.totalForegroundMs }

    fun isPermissionGranted(): Boolean = tracker.isPermissionGranted()
}
