package com.questlog.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.questlog.data.local.entity.BlockedAppEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BlocklistDaoTest {

    private val db: QuestLogDatabase =
        Room.inMemoryDatabaseBuilder<QuestLogDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .addCallback(questLogSeedCallback)
            .build()

    private val dao = db.blocklistDao()

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun `fresh database is seeded with the seven defaults`() = runTest {
        assertEquals(7, dao.getAll().size)
    }

    @Test
    fun `upsert replaces the row for a package and delete removes it`() = runTest {
        dao.upsert(BlockedAppEntity("com.example.x", 10L))
        dao.upsert(BlockedAppEntity("com.example.x", 20L))
        assertEquals(20L, dao.get("com.example.x")?.dailyLimitMs)

        dao.delete("com.example.x")
        assertNull(dao.get("com.example.x"))
    }

    @Test
    fun `observeAll reflects writes`() = runTest {
        val before = dao.observeAll().first().size
        dao.upsert(BlockedAppEntity("com.example.y", 0L))
        assertEquals(before + 1, dao.observeAll().first().size)
    }
}
