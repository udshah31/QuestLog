package com.questlog.domain.usecase

import com.questlog.data.local.dao.CurrencyDao
import com.questlog.data.local.dao.ScreenTimeDao
import com.questlog.data.local.entity.CurrencyBalance
import com.questlog.data.local.entity.ScreenTimeRecord
import com.questlog.data.repository.CurrencyRepository
import com.questlog.data.repository.ScreenTimeRepository
import com.questlog.domain.model.AppUsage
import com.questlog.domain.platform.ScreenTimeTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    override suspend fun totalSavedMsForDate(date: String): Long =
        records.filter { it.date == date }.sumOf { it.savedMs }
}

class FakeCurrencyDao : CurrencyDao {
    var balance = CurrencyBalance(id = 1L, xp = 0L, gold = 0L, gems = 0L, currentLevel = 1, consecutiveDetoxDays = 0, updatedAt = 0L)
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
}

/** Screen-time repo whose "saved time" is fixed and injectable, so reward math can be exercised. */
private class StubScreenTimeRepo(
    private val savedMs: Long,
) : ScreenTimeRepository(FakeScreenTimeDao(), ScreenTimeTracker()) {
    var callCount = 0
        private set
    override suspend fun fetchAndPersistToday(flaggedPackages: Set<String>, startOfDayMs: Long): Long {
        callCount++
        return savedMs
    }
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

        // Verify metrics are computed correctly
        assertTrue(metrics.timeSavedMs >= 0)
        assertTrue(metrics.xpEarned >= 0)
        assertTrue(metrics.goldEarned >= 0)
        assertEquals(1, metrics.currentLevel)
        assertEquals(1.0f, metrics.streakMultiplier)
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
}
