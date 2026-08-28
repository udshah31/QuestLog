package com.example.questlog.ui.dashboard

import com.example.questlog.billing.BillingManager
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
import com.questlog.domain.model.CityTile
import com.questlog.domain.model.DetoxMetrics
import com.questlog.domain.platform.ScreenTimeTracker
import com.questlog.domain.usecase.CalculateDetoxRewardsUseCase
import com.questlog.domain.usecase.DetoxMonitorFlow
import com.questlog.domain.usecase.GetDashboardStatsUseCase
import com.questlog.domain.usecase.PurchaseBuildingUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeScreenTimeDao : ScreenTimeDao {
    val records = mutableListOf<ScreenTimeRecord>()
    override suspend fun upsert(record: ScreenTimeRecord) { records.add(record) }
    override fun getByDate(date: String): Flow<List<ScreenTimeRecord>> = MutableStateFlow(records)
    override fun getSince(fromDate: String): Flow<List<ScreenTimeRecord>> = MutableStateFlow(records)
    override suspend fun totalSavedMsForDate(date: String): Long = records.sumOf { it.savedMs }
}

class FakeCurrencyDao : CurrencyDao {
    var balance = CurrencyBalance(id = 1L, xp = 200L, gold = 150L, gems = 5L, currentLevel = 2, consecutiveDetoxDays = 3)
    val flow = MutableStateFlow<CurrencyBalance?>(balance)
    override suspend fun upsert(b: CurrencyBalance) { balance = b; flow.value = b }
    override suspend fun insertIfAbsent(b: CurrencyBalance) { /* row already present in this fake */ }
    override fun observe(): Flow<CurrencyBalance?> = flow
    override suspend fun get(): CurrencyBalance? = balance
    override suspend fun addRewards(xpDelta: Long, goldDelta: Long, now: Long) {
        balance = balance.copy(xp = balance.xp + xpDelta, gold = balance.gold + goldDelta, updatedAt = now)
        flow.value = balance
    }
    override suspend fun updateDailyAward(date: String, awardedSavedMs: Long, now: Long) {
        balance = balance.copy(rewardDate = date, awardedSavedMsToday = awardedSavedMs, updatedAt = now)
        flow.value = balance
    }
}

class FakeInventoryDao : InventoryDao {
    val items = mutableListOf<InventoryItem>()
    val flow = MutableStateFlow<List<InventoryItem>>(items)
    override suspend fun addItem(item: InventoryItem) { items.add(item); flow.value = items.toList() }
    override fun getByType(type: ItemType): Flow<List<InventoryItem>> = flow
    override fun getAll(): Flow<List<InventoryItem>> = flow
    override suspend fun isOwned(itemId: String): Boolean = items.any { it.itemId == itemId }
}

/** Detox monitor that never emits — keeps the polling loop out of tests that don't exercise it. */
private fun silentMonitor() = object : DetoxMonitorFlow(runDetoxCheck = { error("unused") }) {
    override fun invoke(): Flow<DetoxMetrics> = emptyFlow()
}

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads stats and city buildings`() = runTest {
        val currencyDao = FakeCurrencyDao()
        val inventoryDao = FakeInventoryDao()
        val screenTimeDao = FakeScreenTimeDao()

        val currencyRepo = CurrencyRepository(currencyDao)
        val inventoryRepo = InventoryRepository(inventoryDao)
        val screenTimeRepo = ScreenTimeRepository(screenTimeDao, ScreenTimeTracker())

        val getDashboardStats = GetDashboardStatsUseCase(currencyRepo, inventoryRepo, screenTimeRepo)
        val calculateDetox = CalculateDetoxRewardsUseCase(screenTimeRepo, currencyRepo, setOf("com.instagram.android"))
        val purchaseBuilding = PurchaseBuildingUseCase(currencyRepo, inventoryRepo)
        val billingManager = BillingManager()

        val viewModel = DashboardViewModel(
            getDashboardStats = getDashboardStats,
            calculateDetoxRewards = calculateDetox,
            detoxMonitor = silentMonitor(),
            purchaseBuilding = purchaseBuilding,
            billingManager = billingManager,
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.stats.level)
        assertEquals(150L, state.stats.gold)
        assertEquals(6, state.cityTiles.size)
    }

    @Test
    fun `purchase building with enough gold succeeds`() = runTest {
        val currencyDao = FakeCurrencyDao()
        val inventoryDao = FakeInventoryDao()
        val screenTimeDao = FakeScreenTimeDao()

        val currencyRepo = CurrencyRepository(currencyDao)
        val inventoryRepo = InventoryRepository(inventoryDao)
        val screenTimeRepo = ScreenTimeRepository(screenTimeDao, ScreenTimeTracker())

        val viewModel = DashboardViewModel(
            getDashboardStats = GetDashboardStatsUseCase(currencyRepo, inventoryRepo, screenTimeRepo),
            calculateDetoxRewards = CalculateDetoxRewardsUseCase(screenTimeRepo, currencyRepo, setOf("com.instagram.android")),
            detoxMonitor = silentMonitor(),
            purchaseBuilding = PurchaseBuildingUseCase(currencyRepo, inventoryRepo),
            billingManager = BillingManager(),
        )

        advanceUntilIdle()

        // Market costs 50 gold, user starts with 150 gold
        val marketTile = CityTile("market", "Market", 1, false, false, 50L)
        viewModel.onIntent(DashboardIntent.Purchase(marketTile))

        advanceUntilIdle()

        assertEquals(100L, viewModel.uiState.value.stats.gold)
        assertTrue(inventoryDao.isOwned("market"))
    }

    @Test
    fun `purchase premium building without pro shows paywall`() = runTest {
        val currencyDao = FakeCurrencyDao()
        val inventoryDao = FakeInventoryDao()
        val screenTimeDao = FakeScreenTimeDao()

        val viewModel = DashboardViewModel(
            getDashboardStats = GetDashboardStatsUseCase(CurrencyRepository(currencyDao), InventoryRepository(inventoryDao), ScreenTimeRepository(screenTimeDao, ScreenTimeTracker())),
            calculateDetoxRewards = CalculateDetoxRewardsUseCase(ScreenTimeRepository(screenTimeDao, ScreenTimeTracker()), CurrencyRepository(currencyDao), setOf("com.instagram.android")),
            detoxMonitor = silentMonitor(),
            purchaseBuilding = PurchaseBuildingUseCase(CurrencyRepository(currencyDao), InventoryRepository(inventoryDao)),
            billingManager = BillingManager(),
        )

        advanceUntilIdle()

        val castleTile = CityTile("castle", "Crystal Castle", 3, true, false, 0L)
        viewModel.onIntent(DashboardIntent.Purchase(castleTile))

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showPaywall)
    }

    @Test
    fun `dashboard collects the detox monitor on start`() = runTest {
        val ticks = MutableSharedFlow<DetoxMetrics>()
        val monitor = object : DetoxMonitorFlow(runDetoxCheck = { error("unused") }) {
            override fun invoke(): Flow<DetoxMetrics> = ticks
        }

        val currencyDao = FakeCurrencyDao()
        val inventoryDao = FakeInventoryDao()
        val screenTimeDao = FakeScreenTimeDao()
        val currencyRepo = CurrencyRepository(currencyDao)
        val inventoryRepo = InventoryRepository(inventoryDao)
        val screenTimeRepo = ScreenTimeRepository(screenTimeDao, ScreenTimeTracker())

        DashboardViewModel(
            getDashboardStats = GetDashboardStatsUseCase(currencyRepo, inventoryRepo, screenTimeRepo),
            calculateDetoxRewards = CalculateDetoxRewardsUseCase(screenTimeRepo, currencyRepo, setOf("com.instagram.android")),
            detoxMonitor = monitor,
            purchaseBuilding = PurchaseBuildingUseCase(currencyRepo, inventoryRepo),
            billingManager = BillingManager(),
        )

        advanceUntilIdle()

        assertEquals(1, ticks.subscriptionCount.value)
    }
}
