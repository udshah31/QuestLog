package com.example.questlog.ui.dashboard

import android.app.Activity
import com.example.questlog.billing.BillingManager
import com.example.questlog.billing.MilestoneOfferStore
import com.example.questlog.billing.ProOffer
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.PurchasesErrorCode
import com.questlog.data.local.dao.CurrencyDao
import com.questlog.data.local.dao.InventoryDao
import com.questlog.data.local.dao.QuestDao
import com.questlog.data.local.dao.ScreenTimeDao
import com.questlog.data.local.entity.CurrencyBalance
import com.questlog.data.local.entity.InventoryItem
import com.questlog.data.local.entity.ItemType
import com.questlog.data.local.entity.QuestCompletion
import com.questlog.data.local.entity.ScreenTimeRecord
import com.questlog.data.repository.CurrencyRepository
import com.questlog.data.repository.DailyQuestRepository
import com.questlog.data.repository.InventoryRepository
import com.questlog.data.repository.ScreenTimeRepository
import com.questlog.domain.model.BlockedApp
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
import org.mockito.kotlin.mock

class FakeMilestoneStore(var shownFlag: Boolean = false) : MilestoneOfferStore(mock()) {
    var markCount = 0
    override val shown: Boolean get() = shownFlag
    override fun markShown() { shownFlag = true; markCount++ }
}

/** BillingManager with a scripted offer and a purchase that waits for the test to answer. */
class FakeBilling(var offerResult: ProOffer? = null) : BillingManager() {
    var purchaseCalls = 0
    var onError: ((PurchasesError, Boolean) -> Unit)? = null
    override suspend fun loadProOffer(): ProOffer? = offerResult
    override fun purchasePackage(
        activity: Activity,
        pkg: Package,
        onSuccess: (CustomerInfo) -> Unit,
        onError: (PurchasesError, Boolean) -> Unit,
    ) { purchaseCalls++; this.onError = onError }
}

private fun anOffer() = ProOffer(mock<Package>(), "\$4.99 / month", null)

class FakeScreenTimeDao : ScreenTimeDao {
    val records = mutableListOf<ScreenTimeRecord>()
    override suspend fun upsert(record: ScreenTimeRecord) { records.add(record) }
    override fun getByDate(date: String): Flow<List<ScreenTimeRecord>> = MutableStateFlow(records)
    override fun getSince(fromDate: String): Flow<List<ScreenTimeRecord>> = MutableStateFlow(records)
    override suspend fun totalForegroundMsForDate(date: String): Long = records.sumOf { it.foregroundMs }
    override suspend fun packagesForDate(date: String): List<String> =
        records.map { it.packageName }.distinct()
    override suspend fun foregroundMsForPackageOnDate(packageName: String, date: String): Long =
        records.filter { it.packageName == packageName }.sumOf { it.foregroundMs }
}

class FakeCurrencyDao : CurrencyDao {
    var balance = CurrencyBalance(id = 1L, xp = 200L, gold = 150L, gems = 5L, consecutiveDetoxDays = 3)
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
    override suspend fun setStreak(days: Int, now: Long) {
        balance = balance.copy(consecutiveDetoxDays = days, updatedAt = now)
        flow.value = balance
    }
    override suspend fun addLifetimeSaved(deltaMs: Long, now: Long) {
        balance = balance.copy(lifetimeSavedMs = balance.lifetimeSavedMs + deltaMs, updatedAt = now)
        flow.value = balance
    }
    override suspend fun setStreakFreezeUsed(date: String, now: Long) {
        balance = balance.copy(streakFreezeLastUsed = date, updatedAt = now)
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
    override suspend fun countBuildingsAcquiredSince(sinceMs: Long): Int =
        items.count { it.type == ItemType.BUILDING && it.acquiredAt >= sinceMs }
}

class FakeQuestDao : QuestDao {
    val completed = linkedSetOf<String>()
    private val flow = MutableStateFlow<List<String>>(emptyList())
    override suspend fun insertIfAbsent(completion: QuestCompletion): Long {
        val added = completed.add(completion.questId)
        flow.value = completed.toList()
        return if (added) 1L else -1L
    }
    override fun observeCompletedIds(date: String): Flow<List<String>> = flow
    override suspend fun completedIds(date: String): List<String> = completed.toList()
}

class FakeBlocklistDao : com.questlog.data.local.dao.BlocklistDao {
    override fun observeAll(): Flow<List<com.questlog.data.local.entity.BlockedAppEntity>> = MutableStateFlow(emptyList())
    override suspend fun getAll(): List<com.questlog.data.local.entity.BlockedAppEntity> = emptyList()
    override suspend fun get(packageName: String): com.questlog.data.local.entity.BlockedAppEntity? = null
    override suspend fun upsert(app: com.questlog.data.local.entity.BlockedAppEntity) {}
    override suspend fun delete(packageName: String) {}
}

private fun emptyBlocklistRepo() = com.questlog.data.repository.BlocklistRepository(FakeBlocklistDao())

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

        val getDashboardStats = GetDashboardStatsUseCase(currencyRepo, inventoryRepo, emptyBlocklistRepo())
        val calculateDetox = CalculateDetoxRewardsUseCase(screenTimeRepo, currencyRepo, { listOf(BlockedApp("com.instagram.android", 0L)) })
        val purchaseBuilding = PurchaseBuildingUseCase(currencyRepo, inventoryRepo)
        val billingManager = BillingManager()

        val viewModel = DashboardViewModel(
            getDashboardStats = getDashboardStats,
            calculateDetoxRewards = calculateDetox,
            detoxMonitor = silentMonitor(),
            purchaseBuilding = purchaseBuilding,
            dailyQuestRepo = DailyQuestRepository(FakeQuestDao()),
            billingManager = billingManager,
            milestoneStore = FakeMilestoneStore(),
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
            getDashboardStats = GetDashboardStatsUseCase(currencyRepo, inventoryRepo, emptyBlocklistRepo()),
            calculateDetoxRewards = CalculateDetoxRewardsUseCase(screenTimeRepo, currencyRepo, { listOf(BlockedApp("com.instagram.android", 0L)) }),
            detoxMonitor = silentMonitor(),
            purchaseBuilding = PurchaseBuildingUseCase(currencyRepo, inventoryRepo),
            dailyQuestRepo = DailyQuestRepository(FakeQuestDao()),
            billingManager = BillingManager(),
            milestoneStore = FakeMilestoneStore(),
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
            getDashboardStats = GetDashboardStatsUseCase(CurrencyRepository(currencyDao), InventoryRepository(inventoryDao), emptyBlocklistRepo()),
            calculateDetoxRewards = CalculateDetoxRewardsUseCase(ScreenTimeRepository(screenTimeDao, ScreenTimeTracker()), CurrencyRepository(currencyDao), { listOf(BlockedApp("com.instagram.android", 0L)) }),
            detoxMonitor = silentMonitor(),
            purchaseBuilding = PurchaseBuildingUseCase(CurrencyRepository(currencyDao), InventoryRepository(inventoryDao)),
            dailyQuestRepo = DailyQuestRepository(FakeQuestDao()),
            billingManager = BillingManager(),
            milestoneStore = FakeMilestoneStore(),
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
            getDashboardStats = GetDashboardStatsUseCase(currencyRepo, inventoryRepo, emptyBlocklistRepo()),
            calculateDetoxRewards = CalculateDetoxRewardsUseCase(screenTimeRepo, currencyRepo, { listOf(BlockedApp("com.instagram.android", 0L)) }),
            detoxMonitor = monitor,
            purchaseBuilding = PurchaseBuildingUseCase(currencyRepo, inventoryRepo),
            dailyQuestRepo = DailyQuestRepository(FakeQuestDao()),
            billingManager = BillingManager(),
            milestoneStore = FakeMilestoneStore(),
        )

        advanceUntilIdle()

        assertEquals(1, ticks.subscriptionCount.value)
    }

    private fun milestoneVm(
        streak: Int,
        billing: BillingManager = BillingManager().apply { setDebugPremium(false) },
        store: FakeMilestoneStore = FakeMilestoneStore(),
    ): DashboardViewModel {
        val currencyDao = FakeCurrencyDao().apply {
            balance = balance.copy(consecutiveDetoxDays = streak); flow.value = balance
        }
        val currencyRepo = CurrencyRepository(currencyDao)
        val inventoryRepo = InventoryRepository(FakeInventoryDao())
        return DashboardViewModel(
            getDashboardStats = GetDashboardStatsUseCase(currencyRepo, inventoryRepo, emptyBlocklistRepo()),
            calculateDetoxRewards = CalculateDetoxRewardsUseCase(
                ScreenTimeRepository(FakeScreenTimeDao(), ScreenTimeTracker()), currencyRepo, { emptyList() },
            ),
            detoxMonitor = silentMonitor(),
            purchaseBuilding = PurchaseBuildingUseCase(currencyRepo, inventoryRepo),
            dailyQuestRepo = DailyQuestRepository(FakeQuestDao()),
            billingManager = billing,
            milestoneStore = store,
        )
    }

    @Test
    fun `seven-day streak opens the milestone paywall once`() = runTest {
        val store = FakeMilestoneStore()
        val vm = milestoneVm(streak = 7, store = store)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.showPaywall)
        assertEquals(PaywallReason.Milestone, vm.uiState.value.paywallReason)
        assertEquals(1, store.markCount)
    }

    @Test
    fun `six-day streak does not open the paywall`() = runTest {
        val vm = milestoneVm(streak = 6)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showPaywall)
    }

    @Test
    fun `pro players never see the milestone paywall`() = runTest {
        val store = FakeMilestoneStore()
        val vm = milestoneVm(streak = 7, billing = BillingManager().apply { setDebugPremium(true) }, store = store)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showPaywall)
        assertEquals(0, store.markCount)
    }

    @Test
    fun `already-shown milestone is not shown again`() = runTest {
        val vm = milestoneVm(streak = 12, store = FakeMilestoneStore(shownFlag = true))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showPaywall)
    }

    @Test
    fun `milestone waits until entitlements are known`() = runTest {
        val billing = BillingManager() // entitlementsKnown = false: CustomerInfo not in yet
        val store = FakeMilestoneStore()
        val vm = milestoneVm(streak = 7, billing = billing, store = store)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showPaywall)
        assertEquals(0, store.markCount)

        billing.setDebugPremium(false) // CustomerInfo arrives: not Pro
        advanceUntilIdle()
        assertTrue(vm.uiState.value.showPaywall)
        assertEquals(1, store.markCount)
    }

    @Test
    fun `manual paywall is not hijacked by the milestone`() = runTest {
        val billing = BillingManager()
        val store = FakeMilestoneStore()
        val vm = milestoneVm(streak = 7, billing = billing, store = store)
        advanceUntilIdle()
        vm.onIntent(DashboardIntent.OpenPaywall)
        billing.setDebugPremium(false)
        advanceUntilIdle()
        assertEquals(PaywallReason.Manual, vm.uiState.value.paywallReason)
    }

    @Test
    fun `dismissing a manual paywall at seven days does not bring the milestone one back`() = runTest {
        val billing = BillingManager()
        val store = FakeMilestoneStore()
        val vm = milestoneVm(streak = 7, billing = billing, store = store)
        advanceUntilIdle()
        vm.onIntent(DashboardIntent.OpenPaywall)
        advanceUntilIdle()
        vm.onIntent(DashboardIntent.DismissPaywall)
        billing.setDebugPremium(false) // entitlements arrive after the dismiss
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showPaywall)
        assertEquals(1, store.markCount)
    }

    @Test
    fun `opening the paywall manually loads the offer and settles`() = runTest {
        val vm = milestoneVm(streak = 0)
        advanceUntilIdle()
        vm.onIntent(DashboardIntent.OpenPaywall)
        advanceUntilIdle()
        val s = vm.uiState.value
        assertTrue(s.showPaywall)
        assertEquals(PaywallReason.Manual, s.paywallReason)
        assertFalse(s.offerLoading)
        assertEquals(null, s.proOffer) // Purchases isn't configured in unit tests
    }

    @Test
    fun `buy pro without an offer is a no-op`() = runTest {
        val vm = milestoneVm(streak = 0)
        advanceUntilIdle()
        vm.onIntent(DashboardIntent.OpenPaywall)
        advanceUntilIdle()
        vm.onIntent(DashboardIntent.BuyPro(mock<Activity>()))
        assertFalse(vm.uiState.value.purchasing)
        assertTrue(vm.uiState.value.showPaywall)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.openWithOffer(billing: FakeBilling): DashboardViewModel {
        billing.setDebugPremium(false)
        val vm = milestoneVm(streak = 0, billing = billing)
        advanceUntilIdle()
        vm.onIntent(DashboardIntent.OpenPaywall)
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `a second buy tap while purchasing does not start another purchase`() = runTest {
        val billing = FakeBilling(anOffer())
        val vm = openWithOffer(billing)
        vm.onIntent(DashboardIntent.BuyPro(mock<Activity>()))
        vm.onIntent(DashboardIntent.BuyPro(mock<Activity>()))
        assertEquals(1, billing.purchaseCalls)
        assertTrue(vm.uiState.value.purchasing)
    }

    @Test
    fun `a pending payment is not reported as a failure`() = runTest {
        val billing = FakeBilling(anOffer())
        val vm = openWithOffer(billing)
        vm.onIntent(DashboardIntent.BuyPro(mock<Activity>()))
        billing.onError!!(PurchasesError(PurchasesErrorCode.PaymentPendingError, null), false)
        assertEquals("Payment pending. Pro unlocks once it clears.", vm.uiState.value.snackbarMessage)
        assertFalse(vm.uiState.value.showPaywall)
        assertFalse(vm.uiState.value.purchasing)
    }

    @Test
    fun `a failed re-fetch keeps the offer that already loaded`() = runTest {
        val billing = FakeBilling(anOffer())
        val vm = openWithOffer(billing)
        vm.onIntent(DashboardIntent.DismissPaywall)
        billing.offerResult = null
        vm.onIntent(DashboardIntent.OpenPaywall)
        advanceUntilIdle()
        assertEquals("\$4.99 / month", vm.uiState.value.proOffer?.priceText)
    }

    @Test
    fun `reopening the paywall clears a purchase that never answered`() = runTest {
        val billing = FakeBilling(anOffer())
        val vm = openWithOffer(billing)
        vm.onIntent(DashboardIntent.BuyPro(mock<Activity>()))
        vm.onIntent(DashboardIntent.DismissPaywall)
        vm.onIntent(DashboardIntent.OpenPaywall)
        assertFalse(vm.uiState.value.purchasing)
    }

    @Test
    fun `milestone waits until the player is back on Today`() = runTest {
        val billing = BillingManager()
        val store = FakeMilestoneStore()
        val vm = milestoneVm(streak = 7, billing = billing, store = store)
        advanceUntilIdle()
        vm.onIntent(DashboardIntent.TodayVisible(false))
        billing.setDebugPremium(false)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showPaywall)
        assertEquals(0, store.markCount)

        vm.onIntent(DashboardIntent.TodayVisible(true))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.showPaywall)
        assertEquals(PaywallReason.Milestone, vm.uiState.value.paywallReason)
    }
}
