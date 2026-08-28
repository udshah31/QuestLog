package com.questlog.domain.usecase

import com.questlog.data.local.dao.CurrencyDao
import com.questlog.data.local.dao.ScreenTimeDao
import com.questlog.data.local.entity.CurrencyBalance
import com.questlog.data.local.entity.ScreenTimeRecord
import com.questlog.data.repository.CurrencyRepository
import com.questlog.data.repository.ScreenTimeRepository
import com.questlog.domain.model.AppUsage
import com.questlog.domain.platform.ScreenTimeTracker
import com.questlog.util.TimeConversion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun today() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
private fun daysAgoKey(n: Int) = today().minus(n, DateTimeUnit.DAY).toString()
private const val HOUR_MS = 60 * 60_000L

class FakeScreenTimeDao : ScreenTimeDao {
    val records = mutableListOf<ScreenTimeRecord>()
    override suspend fun upsert(record: ScreenTimeRecord) {
        records.removeAll { it.packageName == record.packageName && it.date == record.date }
        records.add(record)
    }
    override fun getByDate(date: String): Flow<List<ScreenTimeRecord>> =
        MutableStateFlow(records.filter { it.date == date })
    override fun getSince(fromDate: String): Flow<List<ScreenTimeRecord>> =
        MutableStateFlow(records.filter { it.date >= fromDate })
    override suspend fun totalForegroundMsForDate(date: String): Long =
        records.filter { it.date == date }.sumOf { it.foregroundMs }
    override suspend fun foregroundMsForPackageOnDate(packageName: String, date: String): Long =
        records.filter { it.date == date && it.packageName == packageName }.sumOf { it.foregroundMs }
}

class FakeCurrencyDao : CurrencyDao {
    var balance = CurrencyBalance(id = 1L, xp = 0L, gold = 0L, gems = 0L, consecutiveDetoxDays = 0, updatedAt = 0L)
    val flow = MutableStateFlow<CurrencyBalance?>(balance)

    override suspend fun upsert(b: CurrencyBalance) {
        balance = b
        flow.value = b
    }
    override suspend fun insertIfAbsent(b: CurrencyBalance) { /* row already present in this fake */ }
    override fun observe(): Flow<CurrencyBalance?> = flow
    override suspend fun get(): CurrencyBalance? = balance
    override suspend fun addRewards(xpDelta: Long, goldDelta: Long, now: Long) {
        balance = balance.copy(
            xp = balance.xp + xpDelta,
            gold = balance.gold + goldDelta,
            updatedAt = now
        )
        flow.value = balance
    }
    override suspend fun updateDailyAward(date: String, awardedSavedMs: Long, now: Long) {
        balance = balance.copy(rewardDate = date, awardedSavedMsToday = awardedSavedMs, updatedAt = now)
        flow.value = balance
    }
    override suspend fun setStreak(days: Int, now: Long) {
        balance = balance.copy(consecutiveDetoxDays = days, updatedAt = now)
        flow.value = balance
    }
}

/**
 * Screen-time repo whose "saved time" is fixed and injectable, so reward math can be exercised.
 * [foregroundByDate] backs streak evaluation (defaults to 0 for any unseen day).
 */
private class StubScreenTimeRepo(
    private val savedMs: Long,
    private val foregroundByDate: Map<String, Long> = emptyMap(),
) : ScreenTimeRepository(FakeScreenTimeDao(), ScreenTimeTracker()) {
    var callCount = 0
        private set
    override suspend fun fetchAndPersistToday(flaggedPackages: Set<String>, startOfDayMs: Long): Long {
        callCount++
        return savedMs
    }
    override suspend fun totalForegroundMs(date: String): Long = foregroundByDate[date] ?: 0L
}

class CalculateDetoxRewardsUseCaseTest {

    @Test
    fun `invoke calculates rewards and persists them to currency repo`() = runTest {
        val screenTimeDao = FakeScreenTimeDao()
        val currencyDao = FakeCurrencyDao()

        val screenTimeRepo = ScreenTimeRepository(screenTimeDao, ScreenTimeTracker())
        val currencyRepo = CurrencyRepository(currencyDao)
        currencyRepo.ensureInitialized()

        val useCase = CalculateDetoxRewardsUseCase(
            screenTimeRepo = screenTimeRepo,
            currencyRepo = currencyRepo,
            flaggedPackages = setOf("com.instagram.android"),
        )

        val metrics = useCase()

        assertTrue(metrics.timeSavedMs >= 0)
        assertTrue(metrics.xpEarned >= 0)
        assertTrue(metrics.goldEarned >= 0)
        assertEquals(1.0f, metrics.streakMultiplier)
        // Whatever was computed for today is the balance's high-water mark.
        assertEquals(metrics.timeSavedMs, currencyDao.balance.awardedSavedMsToday)
    }

    @Test
    fun `invoke with high streak multiplier scales rewards appropriately`() = runTest {
        val screenTimeDao = FakeScreenTimeDao()
        val currencyDao = FakeCurrencyDao()
        currencyDao.upsert(CurrencyBalance(id = 1L, xp = 500L, gold = 100L, consecutiveDetoxDays = 10))

        val screenTimeRepo = ScreenTimeRepository(screenTimeDao, ScreenTimeTracker())
        val currencyRepo = CurrencyRepository(currencyDao)

        val useCase = CalculateDetoxRewardsUseCase(
            screenTimeRepo = screenTimeRepo,
            currencyRepo = currencyRepo,
            flaggedPackages = setOf("com.instagram.android"),
        )

        val metrics = useCase()

        // 10 consecutive days -> 2.0x multiplier
        assertEquals(2.0f, metrics.streakMultiplier)
        assertEquals(10, metrics.consecutiveDetoxDays)
    }

    @Test
    fun `repeated invocations on the same day do not re-grant the daily reward`() = runTest {
        val currencyDao = FakeCurrencyDao()
        val currencyRepo = CurrencyRepository(currencyDao)
        // 30 minutes saved -> 30 * 10 XP/min * 1.0x = 300 XP, 30 * 2 gold/min = 60 gold
        val screenTimeRepo = StubScreenTimeRepo(savedMs = 30 * 60_000L)

        val useCase = CalculateDetoxRewardsUseCase(
            screenTimeRepo = screenTimeRepo,
            currencyRepo = currencyRepo,
            flaggedPackages = setOf("com.instagram.android"),
        )

        val first = useCase()
        assertEquals(300L, first.xpEarned)
        assertEquals(60L, first.goldEarned)
        assertEquals(300L, currencyDao.balance.xp)
        assertEquals(60L, currencyDao.balance.gold)

        val second = useCase()
        val third = useCase()

        assertEquals(0L, second.xpEarned)
        assertEquals(0L, third.xpEarned)
        assertEquals(300L, currencyDao.balance.xp)
        assertEquals(60L, currencyDao.balance.gold)
    }

    @Test
    fun `additional saved time later in the day grants only the incremental reward`() = runTest {
        val currencyDao = FakeCurrencyDao()
        val currencyRepo = CurrencyRepository(currencyDao)

        val morning = StubScreenTimeRepo(savedMs = 20 * 60_000L) // 200 XP
        useCaseFor(morning, currencyRepo)()
        assertEquals(200L, currencyDao.balance.xp)

        val evening = StubScreenTimeRepo(savedMs = 50 * 60_000L) // cumulative 500 XP
        val eveningMetrics = useCaseFor(evening, currencyRepo)()

        assertEquals(300L, eveningMetrics.xpEarned) // only the delta
        assertEquals(500L, currencyDao.balance.xp)
    }

    private fun useCaseFor(repo: ScreenTimeRepository, currencyRepo: CurrencyRepository) =
        CalculateDetoxRewardsUseCase(repo, currencyRepo, setOf("com.instagram.android"))

    // ── streak tracking ──────────────────────────────────────────────────────

    @Test
    fun `streak increments when the day that just ended stayed under budget`() = runTest {
        val currencyDao = FakeCurrencyDao().apply {
            balance = balance.copy(rewardDate = daysAgoKey(1), consecutiveDetoxDays = 3)
        }
        val repo = StubScreenTimeRepo(
            savedMs = 0L,
            foregroundByDate = mapOf(daysAgoKey(1) to 30 * 60_000L), // 30 min ≤ 60 min budget
        )

        val metrics = useCaseFor(repo, CurrencyRepository(currencyDao))()

        assertEquals(4, currencyDao.balance.consecutiveDetoxDays)
        assertEquals(4, metrics.consecutiveDetoxDays)
        assertEquals(TimeConversion.streakMultiplier(4), metrics.streakMultiplier)
    }

    @Test
    fun `streak resets when the day that just ended went over budget`() = runTest {
        val currencyDao = FakeCurrencyDao().apply {
            balance = balance.copy(rewardDate = daysAgoKey(1), consecutiveDetoxDays = 5)
        }
        val repo = StubScreenTimeRepo(
            savedMs = 0L,
            foregroundByDate = mapOf(daysAgoKey(1) to 90 * 60_000L), // over budget
        )

        useCaseFor(repo, CurrencyRepository(currencyDao))()

        assertEquals(0, currencyDao.balance.consecutiveDetoxDays)
    }

    @Test
    fun `streak is untouched on repeated runs within the same day`() = runTest {
        val currencyDao = FakeCurrencyDao().apply {
            balance = balance.copy(rewardDate = today().toString(), consecutiveDetoxDays = 7)
        }
        val repo = StubScreenTimeRepo(savedMs = 0L)
        val useCase = useCaseFor(repo, CurrencyRepository(currencyDao))

        useCase(); useCase()

        assertEquals(7, currencyDao.balance.consecutiveDetoxDays)
    }

    @Test
    fun `the first ever run does not change the streak`() = runTest {
        val currencyDao = FakeCurrencyDao() // rewardDate = "", consecutiveDetoxDays = 0
        val repo = StubScreenTimeRepo(savedMs = 0L)

        useCaseFor(repo, CurrencyRepository(currencyDao))()

        assertEquals(0, currencyDao.balance.consecutiveDetoxDays)
        assertEquals(today().toString(), currencyDao.balance.rewardDate)
    }

    @Test
    fun `a phone-free multi-day gap counts every skipped day toward the streak`() = runTest {
        val currencyDao = FakeCurrencyDao().apply {
            balance = balance.copy(rewardDate = daysAgoKey(3), consecutiveDetoxDays = 2)
        }
        // last recorded day was under budget; the two days since have no records at all
        val repo = StubScreenTimeRepo(
            savedMs = 0L,
            foregroundByDate = mapOf(daysAgoKey(3) to 10 * 60_000L),
        )

        useCaseFor(repo, CurrencyRepository(currencyDao))()

        assertEquals(5, currencyDao.balance.consecutiveDetoxDays) // 2 + (1 under-budget day + 2 empty days)
    }
}
