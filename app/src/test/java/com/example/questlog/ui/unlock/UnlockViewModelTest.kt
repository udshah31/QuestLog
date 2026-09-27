package com.example.questlog.ui.unlock

import com.example.questlog.data.InstalledApp
import com.example.questlog.data.InstalledAppsProvider
import com.example.questlog.unlock.IntentJudge
import com.example.questlog.unlock.Verdict
import com.questlog.data.local.dao.BlocklistDao
import com.questlog.data.local.dao.MindfulUnlockDao
import com.questlog.data.local.dao.PackageGrace
import com.questlog.data.local.entity.BlockedAppEntity
import com.questlog.data.local.entity.MindfulUnlockEntity
import com.questlog.data.repository.BlocklistRepository
import com.questlog.data.repository.MindfulUnlockRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

private class Blocked(vararg pkgs: String) : BlocklistDao {
    private val rows = pkgs.map { BlockedAppEntity(it, 0L) }
    override fun observeAll(): Flow<List<BlockedAppEntity>> = MutableStateFlow(rows)
    override suspend fun getAll() = rows
    override suspend fun get(packageName: String) = rows.find { it.packageName == packageName }
    override suspend fun upsert(app: BlockedAppEntity) = Unit
    override suspend fun delete(packageName: String) = Unit
}

private class Apps(vararg apps: Pair<String, String>) : InstalledAppsProvider {
    private val list = apps.map { InstalledApp(it.first, it.second, null) }
    override suspend fun launchableApps() = list
}

private class Unlocks : MindfulUnlockDao {
    val rows = mutableListOf<MindfulUnlockEntity>()
    override suspend fun insert(row: MindfulUnlockEntity): Long { rows += row; return rows.size.toLong() }
    override suspend fun graceByPackageForDate(date: String) =
        rows.filter { it.date == date }.groupBy { it.packageName }.map { (p, r) -> PackageGrace(p, r.sumOf { it.graceMs }) }
    override suspend fun countForDate(date: String) = rows.count { it.date == date }
}

@OptIn(ExperimentalCoroutinesApi::class)
class UnlockViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private class Judge(var verdict: Verdict) : IntentJudge("https://unused", { "id" }) {
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun judge(appLabel: String, reason: String): Verdict {
            calls++
            gate?.await()
            return verdict
        }
    }

    private val insta = UnlockApp("com.insta", "Instagram")

    private fun TestScope.vm(
        judge: Judge,
        unlocks: Unlocks = Unlocks(),
        premium: () -> Boolean = { false },
    ): UnlockViewModel {
        val vm = UnlockViewModel(
            blocklistRepo = BlocklistRepository(Blocked("com.insta", "com.gone")),
            installedApps = Apps("com.insta" to "Instagram", "com.other" to "Other"),
            judge = judge,
            unlocks = MindfulUnlockRepository(unlocks),
            isPremium = premium,
        )
        advanceUntilIdle()
        return vm
    }

    private fun UnlockViewModel.ask(reason: String) {
        onIntent(UnlockIntent.Select(uiState.value.apps.single()))
        onIntent(UnlockIntent.SetReason(reason))
        onIntent(UnlockIntent.Check)
    }

    @Test
    fun `lists only blocked apps that are installed`() = runTest {
        assertEquals(listOf("Instagram"), vm(Judge(Verdict.Unavailable)).uiState.value.apps.map { it.label })
    }

    @Test
    fun `a purposeful answer grants grace and records it`() = runTest {
        val unlocks = Unlocks()
        val vm = vm(Judge(Verdict.Judged(0.9, "message")), unlocks)
        vm.ask("reply to mum")
        advanceUntilIdle()
        assertEquals(UnlockPhase.Granted, vm.uiState.value.phase)
        assertEquals(listOf(5 * 60_000L), unlocks.rows.map { it.graceMs })
        vm.onIntent(UnlockIntent.Open)
        assertEquals(UnlockEvent.Launch("com.insta"), vm.events.first())
    }

    @Test
    fun `a drifting answer is recorded with no grace`() = runTest {
        val unlocks = Unlocks()
        val vm = vm(Judge(Verdict.Judged(0.95, "boredom")), unlocks)
        vm.ask("bored")
        advanceUntilIdle()
        assertEquals(UnlockPhase.Drifting, vm.uiState.value.phase)
        assertEquals(listOf(0L), unlocks.rows.map { it.graceMs })
    }

    @Test
    fun `unavailable records nothing and does not use the free unlock`() = runTest {
        val unlocks = Unlocks()
        val vm = vm(Judge(Verdict.Unavailable), unlocks)
        vm.ask("reply to mum")
        advanceUntilIdle()
        assertEquals(UnlockPhase.Unavailable, vm.uiState.value.phase)
        assertEquals(0, unlocks.rows.size)
        assertEquals(1, vm.uiState.value.freeLeft)
    }

    @Test
    fun `free cap opens the paywall without calling the proxy`() = runTest {
        val judge = Judge(Verdict.Judged(0.9, "message"))
        val vm = vm(judge)
        vm.ask("reply to mum"); advanceUntilIdle()
        vm.onIntent(UnlockIntent.Reset)
        vm.ask("check a post"); advanceUntilIdle()
        assertEquals(1, judge.calls)
        assertEquals(UnlockEvent.OpenPaywall, vm.events.first())
    }

    @Test
    fun `pro bought after the cap is honoured on the next check`() = runTest {
        var pro = false
        val judge = Judge(Verdict.Judged(0.9, "message"))
        val vm = vm(judge, premium = { pro })
        vm.ask("reply to mum"); advanceUntilIdle()
        vm.onIntent(UnlockIntent.Reset)
        pro = true
        vm.ask("check a post"); advanceUntilIdle()
        assertEquals(2, judge.calls)
        assertEquals(UnlockPhase.Granted, vm.uiState.value.phase)
    }

    @Test
    fun `a second check while one is in flight is ignored`() = runTest {
        val unlocks = Unlocks()
        val judge = Judge(Verdict.Judged(0.9, "message")).apply { gate = CompletableDeferred() }
        val vm = vm(judge, unlocks, premium = { true })
        vm.ask("reply to mum"); advanceUntilIdle()
        vm.onIntent(UnlockIntent.Check); advanceUntilIdle()
        judge.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(1, judge.calls)
        assertEquals(1, unlocks.rows.size)
    }

    @Test
    fun `blank reason does not check`() = runTest {
        val judge = Judge(Verdict.Judged(0.9, "message"))
        val vm = vm(judge)
        vm.ask("   "); advanceUntilIdle()
        assertEquals(0, judge.calls)
        assertEquals(UnlockPhase.Reason, vm.uiState.value.phase)
    }

    @Test
    fun `reset starts fresh at the app list`() = runTest {
        val vm = vm(Judge(Verdict.Judged(0.9, "message")))
        vm.ask("reply to mum"); advanceUntilIdle()
        vm.onIntent(UnlockIntent.Reset); advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals(UnlockPhase.Pick, s.phase)
        assertEquals(null, s.selected)
        assertEquals("", s.reason)
        assertEquals(1, s.usedToday)
    }

    @Test
    fun `leaving mid-check cancels it - no stale verdict, no extra unlock`() = runTest {
        val unlocks = Unlocks()
        val stale = CompletableDeferred<Unit>()
        val judge = Judge(Verdict.Judged(0.9, "message")).apply { gate = stale }
        val vm = vm(judge, unlocks)
        vm.ask("reply to mum"); advanceUntilIdle()   // in flight, held on the gate
        vm.onIntent(UnlockIntent.Reset); advanceUntilIdle()
        judge.gate = null
        judge.verdict = Verdict.Judged(0.95, "boredom")
        vm.ask("bored"); advanceUntilIdle()            // the new check completes
        stale.complete(Unit); advanceUntilIdle()       // the old one would land now

        assertEquals(UnlockPhase.Drifting, vm.uiState.value.phase)
        assertEquals(listOf("boredom"), unlocks.rows.map { it.category })
    }

    @Test
    fun `nothing is loaded until the app list and quota arrive`() = runTest {
        val vm = UnlockViewModel(
            blocklistRepo = BlocklistRepository(Blocked("com.insta")),
            installedApps = Apps("com.insta" to "Instagram"),
            judge = Judge(Verdict.Unavailable),
            unlocks = MindfulUnlockRepository(Unlocks()),
            isPremium = { false },
        )
        assertEquals(false, vm.uiState.value.loaded)
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.loaded)
    }

    @Test
    fun `no blocked app installed is an empty, loaded list`() = runTest {
        val vm = UnlockViewModel(
            blocklistRepo = BlocklistRepository(Blocked("com.notinstalled")),
            installedApps = Apps("com.insta" to "Instagram"),
            judge = Judge(Verdict.Unavailable),
            unlocks = MindfulUnlockRepository(Unlocks()),
            isPremium = { false },
        )
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.loaded)
        assertEquals(emptyList(), vm.uiState.value.apps)
    }
}
