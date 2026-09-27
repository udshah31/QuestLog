package com.questlog.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.questlog.data.local.entity.QuestCompletion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class QuestDaoCountTest {
    private val db: QuestLogDatabase =
        Room.inMemoryDatabaseBuilder<QuestLogDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
    private val dao = db.questDao()

    @AfterTest fun tearDown() = db.close()

    @Test
    fun `lifetime count spans every day`() = runTest {
        assertEquals(0, dao.observeLifetimeCompletedCount().first())
        dao.insertIfAbsent(QuestCompletion("2026-09-20", "a", 0))
        dao.insertIfAbsent(QuestCompletion("2026-09-21", "a", 0))
        dao.insertIfAbsent(QuestCompletion("2026-09-21", "b", 0))
        assertEquals(3, dao.observeLifetimeCompletedCount().first())
    }
}
