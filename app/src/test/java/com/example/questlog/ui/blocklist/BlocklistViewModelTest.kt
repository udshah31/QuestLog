package com.example.questlog.ui.blocklist

import com.example.questlog.data.InstalledApp
import com.example.questlog.data.InstalledAppsProvider
import com.questlog.data.local.dao.BlocklistDao
import com.questlog.data.local.entity.BlockedAppEntity
import com.questlog.data.repository.BlocklistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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

private class FakeBlocklistDao : BlocklistDao {
    val rows = mutableListOf<BlockedAppEntity>()
    private val flow = MutableStateFlow<List<BlockedAppEntity>>(emptyList())
    private fun emit() { flow.value = rows.sortedBy { it.packageName } }
    override fun observeAll(): Flow<List<BlockedAppEntity>> = flow
    override suspend fun getAll() = rows.sortedBy { it.packageName }
    override suspend fun get(packageName: String) = rows.firstOrNull { it.packageName == packageName }
    override suspend fun upsert(app: BlockedAppEntity) { rows.removeAll { it.packageName == app.packageName }; rows.add(app); emit() }
    override suspend fun delete(packageName: String) { rows.removeAll { it.packageName == packageName }; emit() }
}

private class FakeApps(private val apps: List<InstalledApp>) : InstalledAppsProvider {
    override suspend fun launchableApps() = apps
}

@OptIn(ExperimentalCoroutinesApi::class)
class BlocklistViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(
        apps: List<InstalledApp> = listOf(
            InstalledApp("com.a", "Alpha", null),
            InstalledApp("com.b", "Bravo", null),
        ),
        dao: FakeBlocklistDao = FakeBlocklistDao(),
        permission: Boolean = true,
    ) = BlocklistViewModel(
        blocklistRepo = BlocklistRepository(dao),
        installedApps = FakeApps(apps),
        isUsageAccessGranted = { permission },
    )

    @Test
    fun `rows list every installed app with blocked reflecting the repo`() = runTest {
        val dao = FakeBlocklistDao().apply { rows.add(BlockedAppEntity("com.b", 0L)) }
        val model = vm(dao = dao)
        advanceUntilIdle()

        val state = model.uiState.first()
        assertFalse(state.loading)
        assertEquals(listOf("com.b", "com.a"), state.rows.map { it.packageName }) // blocked first, then A–Z
        assertTrue(state.rows.first { it.packageName == "com.b" }.blocked)
        assertFalse(state.rows.first { it.packageName == "com.a" }.blocked)
    }

    @Test
    fun `ToggleBlocked adds then removes the app`() = runTest {
        val model = vm()
        advanceUntilIdle()

        model.onIntent(BlocklistIntent.ToggleBlocked("com.a"))
        advanceUntilIdle()
        assertTrue(model.uiState.first().rows.first { it.packageName == "com.a" }.blocked)

        model.onIntent(BlocklistIntent.ToggleBlocked("com.a"))
        advanceUntilIdle()
        assertFalse(model.uiState.first().rows.first { it.packageName == "com.a" }.blocked)
    }

    @Test
    fun `SetLimit blocks the app with that allowance`() = runTest {
        val model = vm()
        advanceUntilIdle()

        model.onIntent(BlocklistIntent.SetLimit("com.a", 30 * 60_000L))
        advanceUntilIdle()

        val row = model.uiState.first().rows.first { it.packageName == "com.a" }
        assertTrue(row.blocked)
        assertEquals(30 * 60_000L, row.dailyLimitMs)
    }

    @Test
    fun `SetQuery filters rows by label case-insensitively`() = runTest {
        val model = vm()
        advanceUntilIdle()

        model.onIntent(BlocklistIntent.SetQuery("alp"))
        advanceUntilIdle()

        assertEquals(listOf("com.a"), model.uiState.first().rows.map { it.packageName })
    }

    @Test
    fun `permission flag comes from the checker and RecheckPermission refreshes it`() = runTest {
        var granted = false
        val model = BlocklistViewModel(
            blocklistRepo = BlocklistRepository(FakeBlocklistDao()),
            installedApps = FakeApps(emptyList()),
            isUsageAccessGranted = { granted },
        )
        advanceUntilIdle()
        assertFalse(model.uiState.first().permissionGranted)

        granted = true
        model.onIntent(BlocklistIntent.RecheckPermission)
        advanceUntilIdle()
        assertTrue(model.uiState.first().permissionGranted)
    }
}
