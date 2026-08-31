package com.questlog.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.questlog.data.local.entity.ScreenTimeRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenTimeDaoTest {

    private val db: QuestLogDatabase =
        Room.inMemoryDatabaseBuilder<QuestLogDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()

    private val dao = db.screenTimeDao()

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun `upsert keeps one row per package per day and applies the latest values`() = runTest {
        val pkg = "com.instagram.android"
        val date = "2026-08-27"

        dao.upsert(ScreenTimeRecord(packageName = pkg, date = date, foregroundMs = 5 * 60_000L))
        dao.upsert(ScreenTimeRecord(packageName = pkg, date = date, foregroundMs = 12 * 60_000L))
        dao.upsert(ScreenTimeRecord(packageName = pkg, date = date, foregroundMs = 20 * 60_000L))

        val rows = dao.getByDate(date).first()

        assertEquals(1, rows.size)
        assertEquals(20 * 60_000L, rows.single().foregroundMs)
    }

    @Test
    fun `different packages and different days stay as separate rows`() = runTest {
        dao.upsert(ScreenTimeRecord(packageName = "com.instagram.android", date = "2026-08-27", foregroundMs = 1))
        dao.upsert(ScreenTimeRecord(packageName = "com.snapchat.android", date = "2026-08-27", foregroundMs = 1))
        dao.upsert(ScreenTimeRecord(packageName = "com.instagram.android", date = "2026-08-28", foregroundMs = 1))

        assertEquals(2, dao.getByDate("2026-08-27").first().size)
        assertEquals(1, dao.getByDate("2026-08-28").first().size)
    }

    @Test
    fun `packagesForDate lists the distinct apps tracked on that day`() = runTest {
        dao.upsert(ScreenTimeRecord("com.instagram.android", "2026-08-27", 1))
        dao.upsert(ScreenTimeRecord("com.snapchat.android", "2026-08-27", 1))
        dao.upsert(ScreenTimeRecord("com.instagram.android", "2026-08-27", 2)) // same app, replaces
        dao.upsert(ScreenTimeRecord("com.reddit.frontpage", "2026-08-28", 1))

        assertEquals(
            listOf("com.instagram.android", "com.snapchat.android"),
            dao.packagesForDate("2026-08-27").sorted(),
        )
        assertEquals(listOf("com.reddit.frontpage"), dao.packagesForDate("2026-08-28"))
        assertEquals(emptyList(), dao.packagesForDate("2026-08-29"))
    }
}
