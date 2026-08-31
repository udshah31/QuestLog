package com.questlog.domain.usecase

import com.questlog.data.local.dao.BlocklistDao
import com.questlog.data.local.dao.CurrencyDao
import com.questlog.data.local.dao.InventoryDao
import com.questlog.data.local.entity.BlockedAppEntity
import com.questlog.data.local.entity.CurrencyBalance
import com.questlog.data.local.entity.InventoryItem
import com.questlog.data.local.entity.ItemType
import com.questlog.data.repository.BlocklistRepository
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
    override suspend fun addLifetimeSaved(deltaMs: Long, now: Long) {}
    override suspend fun setStreakFreezeUsed(date: String, now: Long) {}
}

private class StubInventoryDao : InventoryDao {
    override suspend fun addItem(item: InventoryItem) {}
    override fun getByType(type: ItemType): Flow<List<InventoryItem>> = MutableStateFlow(emptyList())
    override fun getAll(): Flow<List<InventoryItem>> = MutableStateFlow(emptyList())
    override suspend fun isOwned(itemId: String): Boolean = false
    override suspend fun countBuildingsAcquiredSince(sinceMs: Long): Int = 0
}

private class StubBlocklistDao(packages: List<String>) : BlocklistDao {
    private val rows = MutableStateFlow(packages.map { BlockedAppEntity(it, 0L) })
    override fun observeAll(): Flow<List<BlockedAppEntity>> = rows
    override suspend fun getAll(): List<BlockedAppEntity> = rows.value
    override suspend fun get(packageName: String): BlockedAppEntity? = rows.value.firstOrNull { it.packageName == packageName }
    override suspend fun upsert(app: BlockedAppEntity) {}
    override suspend fun delete(packageName: String) {}
}

class GetDashboardStatsUseCaseTest {

    private fun useCase(balance: CurrencyBalance, blocked: List<String> = emptyList()) = GetDashboardStatsUseCase(
        currencyRepo = CurrencyRepository(StubCurrencyDao(balance)),
        inventoryRepo = InventoryRepository(StubInventoryDao()),
        blocklistRepo = BlocklistRepository(StubBlocklistDao(blocked)),
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

    @Test
    fun `lifetimeSavedMs is stored total plus today's award`() = runTest {
        val state = useCase(
            CurrencyBalance(id = 1L, lifetimeSavedMs = 5 * 60 * 60_000L, awardedSavedMsToday = 12 * 60_000L),
        ).invoke().first()

        assertEquals(5 * 60 * 60_000L + 12 * 60_000L, state.stats.lifetimeSavedMs)
    }

    @Test
    fun `blockedAppCount reflects the blocklist size`() = runTest {
        val state = useCase(CurrencyBalance(id = 1L), blocked = listOf("com.a", "com.b", "com.c")).invoke().first()

        assertEquals(3, state.blockedAppCount)
    }
}
