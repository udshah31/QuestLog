package com.questlog.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.questlog.data.local.dao.PackageGrace
import com.questlog.data.local.entity.MindfulUnlockEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MindfulUnlockDaoTest {
    private val db: QuestLogDatabase =
        Room.inMemoryDatabaseBuilder<QuestLogDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
    private val dao = db.mindfulUnlockDao()

    @AfterTest fun tearDown() = db.close()

    private fun row(date: String, pkg: String, grace: Long) =
        MindfulUnlockEntity(date = date, packageName = pkg, category = "message", purposeful = 0.9, graceMs = grace, createdAt = 0L)

    @Test
    fun `grace sums per package for the date only`() = runTest {
        dao.insert(row("2026-09-27", "com.insta", 300_000))
        dao.insert(row("2026-09-27", "com.insta", 300_000))
        dao.insert(row("2026-09-27", "com.tiktok", 0))
        dao.insert(row("2026-09-26", "com.insta", 300_000))

        assertEquals(
            setOf(PackageGrace("com.insta", 600_000), PackageGrace("com.tiktok", 0)),
            dao.graceByPackageForDate("2026-09-27").toSet(),
        )
    }

    @Test
    fun `count covers every judged unlock of the date, granted or not`() = runTest {
        dao.insert(row("2026-09-27", "com.insta", 300_000))
        dao.insert(row("2026-09-27", "com.tiktok", 0))
        dao.insert(row("2026-09-26", "com.insta", 300_000))
        assertEquals(2, dao.countForDate("2026-09-27"))
        assertEquals(0, dao.countForDate("2026-09-25"))
    }
}
