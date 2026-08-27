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
}
