package com.questlog.data.repository

import com.questlog.data.local.dao.ScreenTimeDao
import com.questlog.data.local.entity.ScreenTimeRecord
import com.questlog.domain.model.AppUsage
import com.questlog.domain.platform.ScreenTimeTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

open class ScreenTimeRepository(
    private val dao: ScreenTimeDao,
    private val tracker: ScreenTimeTracker,
) {
    private fun today(): String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return "${now.year}-${now.monthNumber.toString().padStart(2,'0')}-${now.dayOfMonth.toString().padStart(2,'0')}"
    }

    open suspend fun fetchAndPersistToday(
        flaggedPackages: Set<String>,
        startOfDayMs: Long,
    ): Long {
        val endMs = Clock.System.now().toEpochMilliseconds()
        val usages: List<AppUsage> = tracker.getUsageForPeriod(startOfDayMs, endMs)
        var totalSavedMs = 0L

        for (usage in usages.filter { it.packageName in flaggedPackages }) {
            // Heuristic: "saved time" is what they would have used minus what they actually used,
            // capped at a reasonable daily goal baseline of 60 minutes per app.
            val dailyGoalMs = 60 * 60_000L
            val savedMs = maxOf(0L, dailyGoalMs - usage.totalForegroundMs)
            totalSavedMs += savedMs
            dao.upsert(
                ScreenTimeRecord(
                    packageName = usage.packageName,
                    date = today(),
                    foregroundMs = usage.totalForegroundMs,
                    savedMs = savedMs,
                )
            )
        }
        return totalSavedMs
    }

    fun observeToday(): Flow<List<ScreenTimeRecord>> = dao.getByDate(today())

    /** Total milliseconds "saved" across all flagged apps so far today. */
    fun observeTodaySavedMs(): Flow<Long> =
        dao.getByDate(today()).map { records -> records.sumOf { it.savedMs } }

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
