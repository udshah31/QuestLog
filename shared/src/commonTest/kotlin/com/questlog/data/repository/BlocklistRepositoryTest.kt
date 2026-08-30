package com.questlog.data.repository

import com.questlog.data.local.dao.BlocklistDao
import com.questlog.data.local.entity.BlockedAppEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeBlocklistDao : BlocklistDao {
    val rows = mutableListOf<BlockedAppEntity>()
    private val flow = MutableStateFlow<List<BlockedAppEntity>>(emptyList())
    private fun emit() { flow.value = rows.sortedBy { it.packageName } }
    override fun observeAll(): Flow<List<BlockedAppEntity>> = flow
    override suspend fun getAll(): List<BlockedAppEntity> = rows.sortedBy { it.packageName }
    override suspend fun get(packageName: String): BlockedAppEntity? =
        rows.firstOrNull { it.packageName == packageName }
    override suspend fun upsert(app: BlockedAppEntity) {
        rows.removeAll { it.packageName == app.packageName }
        rows.add(app); emit()
    }
    override suspend fun delete(packageName: String) {
        rows.removeAll { it.packageName == packageName }; emit()
    }
}

class BlocklistRepositoryTest {

    @Test
    fun `setBlocked true inserts a fully-blocked row and false deletes it`() = runTest {
        val dao = FakeBlocklistDao()
        val repo = BlocklistRepository(dao)

        repo.setBlocked("com.instagram.android", blocked = true)
        assertEquals(0L, repo.current().single().dailyLimitMs)

        repo.setBlocked("com.instagram.android", blocked = false)
        assertTrue(repo.current().isEmpty())
    }

    @Test
    fun `setBlocked true keeps an existing limit`() = runTest {
        val dao = FakeBlocklistDao()
        val repo = BlocklistRepository(dao)
        repo.setLimit("com.reddit.frontpage", 30 * 60_000L)

        repo.setBlocked("com.reddit.frontpage", blocked = true)

        assertEquals(30 * 60_000L, repo.current().single().dailyLimitMs)
    }

    @Test
    fun `setLimit on an unblocked app blocks it with that limit`() = runTest {
        val repo = BlocklistRepository(FakeBlocklistDao())

        repo.setLimit("com.snapchat.android", 15 * 60_000L)

        val app = repo.current().single()
        assertEquals("com.snapchat.android", app.packageName)
        assertEquals(15 * 60_000L, app.dailyLimitMs)
    }

    @Test
    fun `observeBlockedApps emits the current list as domain models`() = runTest {
        val repo = BlocklistRepository(FakeBlocklistDao())
        repo.setBlocked("a", true)
        repo.setLimit("b", 1_000L)

        val list = repo.observeBlockedApps().first()

        assertEquals(listOf("a" to 0L, "b" to 1_000L), list.map { it.packageName to it.dailyLimitMs })
    }

    @Test
    fun `get returns null for an app that was never blocked`() = runTest {
        assertNull(FakeBlocklistDao().get("nope"))
    }
}
