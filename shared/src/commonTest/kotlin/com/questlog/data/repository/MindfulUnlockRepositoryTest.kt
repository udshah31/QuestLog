package com.questlog.data.repository

import com.questlog.data.local.dao.MindfulUnlockDao
import com.questlog.data.local.dao.PackageGrace
import com.questlog.data.local.entity.MindfulUnlockEntity
import com.questlog.domain.unlock.MindfulUnlockRule.GRACE_MS
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

/** Models Room: insert assigns ids; queries filter by date and group by package. */
class FakeMindfulUnlockDao : MindfulUnlockDao {
    val rows = mutableListOf<MindfulUnlockEntity>()
    override suspend fun insert(row: MindfulUnlockEntity): Long {
        val id = rows.size + 1L
        rows += row.copy(id = id)
        return id
    }
    override suspend fun graceByPackageForDate(date: String): List<PackageGrace> =
        rows.filter { it.date == date }.groupBy { it.packageName }
            .map { (pkg, rs) -> PackageGrace(pkg, rs.sumOf { it.graceMs }) }
    override suspend fun countForDate(date: String): Int = rows.count { it.date == date }
}

private class UnlockTestClock(private val at: Instant) : Clock { override fun now() = at }

class MindfulUnlockRepositoryTest {
    // 2026-09-27T10:00Z, read in UTC
    private val clock = UnlockTestClock(Instant.parse("2026-09-27T10:00:00Z"))

    @Test
    fun `record stores today's date and the rule's grace, and returns it`() = runTest {
        val dao = FakeMindfulUnlockDao()
        val repo = MindfulUnlockRepository(dao, clock, TimeZone.UTC)

        assertEquals(GRACE_MS, repo.record("com.insta", "message", 0.9))
        assertEquals(0L, repo.record("com.insta", "boredom", 0.9))

        assertEquals(listOf("2026-09-27", "2026-09-27"), dao.rows.map { it.date })
        assertEquals(listOf(GRACE_MS, 0L), dao.rows.map { it.graceMs })
    }

    @Test
    fun `grace and count are today's only`() = runTest {
        val dao = FakeMindfulUnlockDao().apply {
            rows += MindfulUnlockEntity(1, "2026-09-26", "com.insta", "message", 0.9, GRACE_MS, 0)
        }
        val repo = MindfulUnlockRepository(dao, clock, TimeZone.UTC)
        repo.record("com.insta", "message", 0.9)
        repo.record("com.tiktok", "lookup", 0.8)

        assertEquals(mapOf("com.insta" to GRACE_MS, "com.tiktok" to GRACE_MS), repo.graceMsToday())
        assertEquals(2, repo.countToday())
    }
}
