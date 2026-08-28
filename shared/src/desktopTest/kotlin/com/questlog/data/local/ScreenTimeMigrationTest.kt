package com.questlog.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreenTimeMigrationTest {

    private val dbFile = Files.createTempFile("questlog-migration-test", ".db")

    private val helper = MigrationTestHelper(
        schemaDirectoryPath = Paths.get(System.getProperty("questlog.schemasDir") ?: "schemas"),
        databasePath = dbFile,
        driver = BundledSQLiteDriver(),
        databaseClass = QuestLogDatabase::class,
    )

    @AfterTest
    fun cleanUp() {
        Files.deleteIfExists(dbFile)
    }

    private fun SQLiteConnection.queryLongs(sql: String): List<List<Long>> = buildList {
        prepare(sql).use { stmt ->
            val cols = stmt.getColumnCount()
            while (stmt.step()) add((0 until cols).map { stmt.getLong(it) })
        }
    }

    @Test
    fun `migrating 2 to 3 validates against the exported schema and dedupes rows`() = runTest {
        val v2 = helper.createDatabase(2)
        // Three writes for the same app/day (autogen ids 1, 2, 3) plus one for another app.
        v2.execSQL("INSERT INTO screen_time_records (packageName, date, foregroundMs, savedMs) VALUES ('com.instagram.android', '2026-08-27', 300000, 3300000)")
        v2.execSQL("INSERT INTO screen_time_records (packageName, date, foregroundMs, savedMs) VALUES ('com.instagram.android', '2026-08-27', 720000, 2880000)")
        v2.execSQL("INSERT INTO screen_time_records (packageName, date, foregroundMs, savedMs) VALUES ('com.instagram.android', '2026-08-27', 1200000, 2400000)")
        v2.execSQL("INSERT INTO screen_time_records (packageName, date, foregroundMs, savedMs) VALUES ('com.snapchat.android', '2026-08-27', 60000, 3540000)")
        v2.close()

        // Runs MIGRATION_2_3 and validates the resulting schema against 3.json.
        val v3 = helper.runMigrationsAndValidate(3, listOf(MIGRATION_2_3))

        val total = v3.queryLongs("SELECT COUNT(*) FROM screen_time_records").single().single()
        assertEquals(2L, total, "duplicate (packageName, date) rows should have been collapsed")

        val instagram = v3.queryLongs(
            "SELECT foregroundMs, savedMs FROM screen_time_records " +
                "WHERE packageName = 'com.instagram.android' AND date = '2026-08-27'"
        )
        assertEquals(1, instagram.size)
        assertEquals(listOf(1_200_000L, 2_400_000L), instagram.single(), "the most recent write must win")

        // The composite primary key must now reject a duplicate insert.
        val rejectedDuplicate = runCatching {
            v3.execSQL("INSERT INTO screen_time_records (packageName, date, foregroundMs, savedMs) VALUES ('com.snapchat.android', '2026-08-27', 1, 1)")
        }.isFailure
        assertTrue(rejectedDuplicate, "(packageName, date) should be a primary key after v3")

        v3.close()
    }

    @Test
    fun `migrating 1 to 6 runs the full chain`() = runTest {
        helper.createDatabase(1).close()

        val v6 = helper.runMigrationsAndValidate(6, questLogMigrations.toList())

        // v2 columns present, v5 dropped currentLevel
        v6.execSQL("INSERT INTO currency_balance (id, xp, gold, gems, consecutiveDetoxDays, rewardDate, awardedSavedMsToday, updatedAt) VALUES (1, 0, 0, 0, 0, '2026-08-27', 60000, 0)")
        assertEquals(60_000L, v6.queryLongs("SELECT awardedSavedMsToday FROM currency_balance WHERE id = 1").single().single())
        // v3 key + v6 dropped savedMs
        v6.execSQL("INSERT INTO screen_time_records (packageName, date, foregroundMs) VALUES ('com.instagram.android', '2026-08-27', 123)")
        assertEquals(123L, v6.queryLongs("SELECT foregroundMs FROM screen_time_records").single().single())
        // v4 table
        assertEquals(0L, v6.queryLongs("SELECT COUNT(*) FROM quest_completions").single().single())

        v6.close()
    }

    @Test
    fun `4 to 5 drops the unused currentLevel column`() = runTest {
        helper.createDatabase(4).close()

        val v5 = helper.runMigrationsAndValidate(5, listOf(MIGRATION_4_5))

        val columnGone = runCatching {
            v5.execSQL("SELECT currentLevel FROM currency_balance")
        }.isFailure
        assertTrue(columnGone, "currentLevel should no longer exist after v5")

        v5.close()
    }

    @Test
    fun `5 to 6 drops the per-app savedMs column`() = runTest {
        helper.createDatabase(5).close()

        val v6 = helper.runMigrationsAndValidate(6, listOf(MIGRATION_5_6))

        val columnGone = runCatching {
            v6.execSQL("SELECT savedMs FROM screen_time_records")
        }.isFailure
        assertTrue(columnGone, "savedMs should no longer exist after v6")

        v6.close()
    }

    @Test
    fun `3 to 4 adds quest_completions keyed by date and questId`() = runTest {
        helper.createDatabase(3).close()

        val v4 = helper.runMigrationsAndValidate(4, listOf(MIGRATION_3_4))

        v4.execSQL("INSERT INTO quest_completions (date, questId, completedAt) VALUES ('2026-08-28', 'digital_fasting', 111)")
        assertEquals(1L, v4.queryLongs("SELECT COUNT(*) FROM quest_completions").single().single())

        val rejectedDuplicate = runCatching {
            v4.execSQL("INSERT INTO quest_completions (date, questId, completedAt) VALUES ('2026-08-28', 'digital_fasting', 222)")
        }.isFailure
        assertTrue(rejectedDuplicate, "(date, questId) should be the primary key")

        v4.close()
    }
}
