package com.questlog.domain.usecase

import com.questlog.data.local.dao.CurrencyDao
import com.questlog.data.local.dao.InventoryDao
import com.questlog.data.local.entity.CurrencyBalance
import com.questlog.data.local.entity.InventoryItem
import com.questlog.data.local.entity.ItemType
import com.questlog.data.repository.CurrencyRepository
import com.questlog.data.repository.InventoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class StubCurrencyDao(balance: CurrencyBalance) : CurrencyDao {
    private val flow = MutableStateFlow<CurrencyBalance?>(balance)
    override suspend fun upsert(balance: CurrencyBalance) {}
    override suspend fun insertIfAbsent(balance: CurrencyBalance) {}
    override fun observe(): Flow<CurrencyBalance?> = flow
    override suspend fun get(): CurrencyBalance? = flow.value
    override suspend fun addRewards(xpDelta: Long, goldDelta: Long, now: Long) {}
    override suspend fun updateDailyAward(date: String, awardedSavedMs: Long, now: Long) {}
    override suspend fun setStreak(days: Int, now: Long) {}
    override suspend fun setStreakFreezeUsed(date: String, now: Long) {}
}

private class StubInventoryDao : InventoryDao {
    override suspend fun addItem(item: InventoryItem) {}
    override fun getByType(type: ItemType): Flow<List<InventoryItem>> = MutableStateFlow(emptyList())
    override fun getAll(): Flow<List<InventoryItem>> = MutableStateFlow(emptyList())
    override suspend fun isOwned(itemId: String): Boolean = false
    override suspend fun countBuildingsAcquiredSince(sinceMs: Long): Int = 0
}

class GetDashboardStatsUseCaseTest {

    private fun useCase(balance: CurrencyBalance) = GetDashboardStatsUseCase(
        currencyRepo = CurrencyRepository(StubCurrencyDao(balance)),
        inventoryRepo = InventoryRepository(StubInventoryDao()),
    )

    @Test
    fun `todaySavedMs mirrors the currency balance's daily high-water mark`() = runTest {
        val state = useCase(CurrencyBalance(id = 1L, awardedSavedMsToday = 42 * 60_000L)).invoke().first()

        assertEquals(42 * 60_000L, state.stats.todaySavedMs)
    }

    @Test
    fun `todaySavedMs is zero when nothing has been awarded today`() = runTest {
        val state = useCase(CurrencyBalance(id = 1L)).invoke().first()

        assertEquals(0L, state.stats.todaySavedMs)
    }

    @Test
    fun `stats and buildings are still surfaced`() = runTest {
        val state = useCase(CurrencyBalance(id = 1L, xp = 300L, gold = 120L)).invoke().first()

        assertEquals(300L, state.stats.xp)
        assertEquals(120L, state.stats.gold)
        assertEquals(6, state.cityTiles.size)
    }
}
