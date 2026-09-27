package com.questlog.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.questlog.data.local.entity.DailySaved
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DailySavedDaoTest {
    private val db: QuestLogDatabase =
        Room.inMemoryDatabaseBuilder<QuestLogDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
    private val dao = db.dailySavedDao()

    @AfterTest fun tearDown() = db.close()

    @Test
    fun `upsert replaces the row for the same date`() = runTest {
        dao.upsert(DailySaved("2026-09-20", 10))
        dao.upsert(DailySaved("2026-09-20", 20))
        assertEquals(listOf(DailySaved("2026-09-20", 20)), dao.observeSince("2026-01-01").first())
    }

    @Test
    fun `observeSince is inclusive and ascending`() = runTest {
        dao.upsert(DailySaved("2026-09-22", 3))
        dao.upsert(DailySaved("2026-09-20", 1))
        dao.upsert(DailySaved("2026-09-21", 2))
        assertEquals(listOf("2026-09-21", "2026-09-22"), dao.observeSince("2026-09-21").first().map { it.date })
    }

    @Test
    fun `observeBestMs is the max, and null on an empty table`() = runTest {
        assertNull(dao.observeBestMs().first())
        dao.upsert(DailySaved("2026-09-20", 5))
        dao.upsert(DailySaved("2026-09-21", 40))
        dao.upsert(DailySaved("2026-09-22", 0))
        assertEquals(40L, dao.observeBestMs().first())
    }
}
