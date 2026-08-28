package com.questlog.domain.usecase

import com.questlog.data.local.dao.CurrencyDao
import com.questlog.data.local.dao.InventoryDao
import com.questlog.data.local.dao.ScreenTimeDao
import com.questlog.data.local.entity.CurrencyBalance
import com.questlog.data.local.entity.InventoryItem
import com.questlog.data.local.entity.ItemType
import com.questlog.data.local.entity.ScreenTimeRecord
import com.questlog.data.repository.CurrencyRepository
import com.questlog.data.repository.InventoryRepository
import com.questlog.data.repository.ScreenTimeRepository
import com.questlog.domain.platform.ScreenTimeTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class StubCurrencyDao : CurrencyDao {
    private val flow = MutableStateFlow<CurrencyBalance?>(CurrencyBalance(id = 1L))
    override suspend fun upsert(balance: CurrencyBalance) {}
    override suspend fun insertIfAbsent(balance: CurrencyBalance) {}
    override fun observe(): Flow<CurrencyBalance?> = flow
    override suspend fun get(): CurrencyBalance? = flow.value
    override suspend fun addRewards(xpDelta: Long, goldDelta: Long, now: Long) {}
    override suspend fun updateDailyAward(date: String, awardedSavedMs: Long, now: Long) {}
}

private class StubInventoryDao : InventoryDao {
    override suspend fun addItem(item: InventoryItem) {}
    override fun getByType(type: ItemType): Flow<List<InventoryItem>> = MutableStateFlow(emptyList())
    override fun getAll(): Flow<List<InventoryItem>> = MutableStateFlow(emptyList())
    override suspend fun isOwned(itemId: String): Boolean = false
}

private class StubScreenTimeDao(savedMsPerApp: List<Long>) : ScreenTimeDao {
    private val rows = savedMsPerApp.mapIndexed { i, ms ->
        ScreenTimeRecord(packageName = "pkg$i", date = "today", foregroundMs = 0L, savedMs = ms)
    }
    override suspend fun upsert(record: ScreenTimeRecord) {}
    override fun getByDate(date: String): Flow<List<ScreenTimeRecord>> = MutableStateFlow(rows)
    override fun getSince(fromDate: String): Flow<List<ScreenTimeRecord>> = MutableStateFlow(emptyList())
    override suspend fun totalSavedMsForDate(date: String): Long = rows.sumOf { it.savedMs }
}

class GetDashboardStatsUseCaseTest {

    private fun useCase(savedMsPerApp: List<Long>): GetDashboardStatsUseCase =
        GetDashboardStatsUseCase(
            currencyRepo = CurrencyRepository(StubCurrencyDao()),
            inventoryRepo = InventoryRepository(StubInventoryDao()),
            screenTimeRepo = ScreenTimeRepository(StubScreenTimeDao(savedMsPerApp), ScreenTimeTracker()),
        )

    @Test
    fun `todaySavedMs is the sum of saved time persisted for today`() = runTest {
        val state = useCase(savedMsPerApp = listOf(20 * 60_000L, 15 * 60_000L)).invoke().first()

        assertEquals(35 * 60_000L, state.stats.todaySavedMs)
    }

    @Test
    fun `todaySavedMs is zero when nothing has been persisted today`() = runTest {
        val state = useCase(savedMsPerApp = emptyList()).invoke().first()

        assertEquals(0L, state.stats.todaySavedMs)
    }
}
