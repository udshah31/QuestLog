package com.questlog.domain.usecase

import com.questlog.data.local.dao.BlocklistDao
import com.questlog.data.local.dao.DailySavedDao
import com.questlog.data.local.dao.QuestDao
import com.questlog.data.local.entity.BlockedAppEntity
import com.questlog.data.local.entity.DailySaved
import com.questlog.data.local.entity.QuestCompletion
import com.questlog.data.repository.BlocklistRepository
import com.questlog.data.repository.CurrencyRepository
import com.questlog.data.repository.DailySavedRepository
import com.questlog.domain.model.ProgressStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class ProgressClock(private val at: Instant) : Clock { override fun now() = at }

private class HistoryDao(rows: List<DailySaved>) : DailySavedDao {
    private val all = rows
    override suspend fun upsert(row: DailySaved) = Unit
    override fun observeSince(fromDate: String): Flow<List<DailySaved>> =
        MutableStateFlow(all.filter { it.date >= fromDate }.sortedBy { it.date })
    override fun observeBestMsBefore(date: String): Flow<Long?> =
        MutableStateFlow(all.filter { it.date < date }.maxOfOrNull { it.savedMs })
}

private class CountQuestDao(private val count: Int) : QuestDao {
    override suspend fun insertIfAbsent(completion: QuestCompletion) = 1L
    override fun observeCompletedIds(date: String): Flow<List<String>> = MutableStateFlow(emptyList())
    override suspend fun completedIds(date: String) = emptyList<String>()
    override fun observeLifetimeCompletedCount(): Flow<Int> = MutableStateFlow(count)
}

private class TwoBlocked : BlocklistDao {
    private val rows = listOf(BlockedAppEntity("a", 0), BlockedAppEntity("b", 0))
    override fun observeAll(): Flow<List<BlockedAppEntity>> = MutableStateFlow(rows)
    override suspend fun getAll() = rows
    override suspend fun get(packageName: String) = rows.find { it.packageName == packageName }
    override suspend fun upsert(app: BlockedAppEntity) = Unit
    override suspend fun delete(packageName: String) = Unit
}

class GetProgressStatsUseCaseTest {
    // "today" is 2026-09-27 in UTC
    private val clock = ProgressClock(Instant.parse("2026-09-27T12:00:00Z"))
    private val today = LocalDate(2026, 9, 27)

    private suspend fun stats(history: List<DailySaved>, todayMs: Long, streak: Int = 4): ProgressStats {
        val currencyDao = FakeCurrencyDao().apply {
            balance = balance.copy(rewardDate = "2026-09-27", awardedSavedMsToday = todayMs, lifetimeSavedMs = 3_600_000L, consecutiveDetoxDays = streak, xp = 150)
            flow.value = balance
        }
        return GetProgressStatsUseCase(
            currencyRepo = CurrencyRepository(currencyDao, clock, TimeZone.UTC),
            dailySavedRepo = DailySavedRepository(HistoryDao(history)),
            questDao = CountQuestDao(7),
            blocklistRepo = BlocklistRepository(TwoBlocked()),
            clock = clock,
            timeZone = TimeZone.UTC,
            dates = kotlinx.coroutines.flow.flowOf(today),
        )().first()
    }

    @Test
    fun `seven contiguous days ending today, missing days are zero`() = runTest {
        val s = stats(listOf(DailySaved("2026-09-25", 30 * 60_000L)), todayMs = 5 * 60_000L)
        assertEquals(7, s.last7Days.size)
        assertEquals((6 downTo 0).map { LocalDate(2026, 9, 27 - it) }, s.last7Days.map { it.date })
        assertTrue(s.last7Days.last().isToday)
        assertEquals(listOf(0L, 0L, 0L, 0L, 30 * 60_000L, 0L, 5 * 60_000L), s.last7Days.map { it.savedMs })
    }

    @Test
    fun `today uses the live value even if a row exists for today`() = runTest {
        val s = stats(listOf(DailySaved("2026-09-27", 99 * 60_000L)), todayMs = 5 * 60_000L)
        assertEquals(5 * 60_000L, s.last7Days.last().savedMs)
    }

    @Test
    fun `best day includes today, and an empty table falls back to today`() = runTest {
        assertEquals(20 * 60_000L, stats(listOf(DailySaved("2026-09-01", 10 * 60_000L)), todayMs = 20 * 60_000L).bestDayMs)
        assertEquals(50 * 60_000L, stats(listOf(DailySaved("2026-09-01", 50 * 60_000L)), todayMs = 20 * 60_000L).bestDayMs)
        assertEquals(20 * 60_000L, stats(emptyList(), todayMs = 20 * 60_000L).bestDayMs)
    }

    @Test
    fun `a malformed date row is ignored`() = runTest {
        val s = stats(listOf(DailySaved("2026-09-26", 10 * 60_000L), DailySaved("2026-9-26x", 99 * 60_000L)), todayMs = 0L)
        assertEquals(7, s.last7Days.size)
        assertEquals(10 * 60_000L, s.last7Days[5].savedMs)
    }

    @Test
    fun `lifetime numbers pass through`() = runTest {
        val s = stats(emptyList(), todayMs = 60_000L, streak = 4)
        assertEquals(4, s.streakDays)
        assertEquals(3_600_000L + 60_000L, s.reclaimedAllTimeMs)
        assertEquals(2, s.appsGuarded)
        assertEquals(7, s.questsCleared)
        assertEquals(150L, s.xp)
        assertEquals(today, s.last7Days.last().date)
    }

    @Test
    fun `an open screen rolls its week over at midnight`() = runTest {
        val currencyDao = FakeCurrencyDao().apply {
            balance = balance.copy(awardedSavedMsToday = 5 * 60_000L); flow.value = balance
        }
        val days = MutableStateFlow(LocalDate(2026, 9, 27))
        val useCase = GetProgressStatsUseCase(
            currencyRepo = CurrencyRepository(currencyDao),
            dailySavedRepo = DailySavedRepository(HistoryDao(listOf(DailySaved("2026-09-27", 40 * 60_000L)))),
            questDao = CountQuestDao(0),
            blocklistRepo = BlocklistRepository(TwoBlocked()),
            dates = days,
        )
        val seen = mutableListOf<ProgressStats>()
        backgroundScope.launch { useCase().collect { seen += it } }
        runCurrent()
        assertEquals(LocalDate(2026, 9, 27), seen.last().last7Days.last().date)

        days.value = LocalDate(2026, 9, 28) // midnight passes while collecting
        runCurrent()

        val week = seen.last().last7Days
        assertEquals(LocalDate(2026, 9, 28), week.last().date)
        assertEquals(40 * 60_000L, week[5].savedMs, "yesterday's finalised row now shows")
    }

    @Test
    fun `a stray row for today never raises best day`() = runTest {
        val s = stats(listOf(DailySaved("2026-09-27", 99 * 60_000L), DailySaved("2026-09-20", 10 * 60_000L)), todayMs = 5 * 60_000L)
        assertEquals(10 * 60_000L, s.bestDayMs)
    }
}
