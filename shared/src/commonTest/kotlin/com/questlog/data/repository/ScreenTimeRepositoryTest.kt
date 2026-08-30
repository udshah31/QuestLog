package com.questlog.data.repository

import com.questlog.data.local.dao.ScreenTimeDao
import com.questlog.data.local.entity.ScreenTimeRecord
import com.questlog.domain.model.AppUsage
import com.questlog.domain.platform.ScreenTimeTracker
import com.questlog.util.DetoxBudget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class MapScreenTimeDao : ScreenTimeDao {
    val records = mutableListOf<ScreenTimeRecord>()
    override suspend fun upsert(record: ScreenTimeRecord) {
        records.removeAll { it.packageName == record.packageName && it.date == record.date }
        records.add(record)
    }
    override fun getByDate(date: String): Flow<List<ScreenTimeRecord>> = MutableStateFlow(records)
    override fun getSince(fromDate: String): Flow<List<ScreenTimeRecord>> = MutableStateFlow(records)
    override suspend fun totalForegroundMsForDate(date: String): Long =
        records.filter { it.date == date }.sumOf { it.foregroundMs }
    override suspend fun foregroundMsForPackageOnDate(packageName: String, date: String): Long =
        records.filter { it.date == date && it.packageName == packageName }.sumOf { it.foregroundMs }
}

/** Tracker that returns a fixed usage list regardless of the window. */
private class StubTracker(private val usage: List<AppUsage>) : ScreenTimeTracker() {
    override suspend fun getUsageForPeriod(startMs: Long, endMs: Long): List<AppUsage> = usage
    override fun isPermissionGranted(): Boolean = true
}

class ScreenTimeRepositoryTest {

    private val budget = 90 * 60_000L

    @Test
    fun `with no allowances every flagged millisecond is charged`() = runTest {
        val repo = ScreenTimeRepository(
            MapScreenTimeDao(),
            StubTracker(listOf(AppUsage("com.insta", 20 * 60_000L))),
            dailyBudgetMs = budget,
        )
        // elapsed is large; saved = budget - 20min
        val saved = repo.fetchAndPersistToday(setOf("com.insta"), startOfDayMs = 0L)
        assertEquals(budget - 20 * 60_000L, saved)
    }

    @Test
    fun `usage within an app's allowance is not charged`() = runTest {
        val repo = ScreenTimeRepository(
            MapScreenTimeDao(),
            StubTracker(listOf(AppUsage("com.insta", 20 * 60_000L))),
            dailyBudgetMs = budget,
        )
        val saved = repo.fetchAndPersistToday(
            flaggedPackages = setOf("com.insta"),
            startOfDayMs = 0L,
            allowances = mapOf("com.insta" to 30 * 60_000L),
        )
        assertEquals(budget, saved, "20min < 30min allowance -> nothing charged")
    }

    @Test
    fun `only the overage beyond the allowance is charged`() = runTest {
        val repo = ScreenTimeRepository(
            MapScreenTimeDao(),
            StubTracker(listOf(AppUsage("com.insta", 50 * 60_000L))),
            dailyBudgetMs = budget,
        )
        val saved = repo.fetchAndPersistToday(
            flaggedPackages = setOf("com.insta"),
            startOfDayMs = 0L,
            allowances = mapOf("com.insta" to 30 * 60_000L),
        )
        assertEquals(budget - 20 * 60_000L, saved, "50min - 30min allowance = 20min charged")
    }

    @Test
    fun `raw foreground is still persisted regardless of the allowance`() = runTest {
        val dao = MapScreenTimeDao()
        val repo = ScreenTimeRepository(
            dao,
            StubTracker(listOf(AppUsage("com.insta", 50 * 60_000L))),
            dailyBudgetMs = budget,
        )
        repo.fetchAndPersistToday(setOf("com.insta"), 0L, mapOf("com.insta" to 30 * 60_000L))
        assertEquals(50 * 60_000L, dao.records.single().foregroundMs)
    }
}
