package com.questlog.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers

object DatabaseFactory {

    /** v2: track how much of today's "saved" screen-time has already been converted to rewards. */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE currency_balance ADD COLUMN rewardDate TEXT NOT NULL DEFAULT ''")
            connection.execSQL("ALTER TABLE currency_balance ADD COLUMN awardedSavedMsToday INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * v3: re-key screen_time_records on (packageName, date) so re-persisting today's usage
     * replaces the row instead of appending. Recreates the table (SQLite can't alter a
     * primary key) and collapses any duplicate rows, keeping the most recently written one.
     */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE `screen_time_records_new` (" +
                    "`packageName` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                    "`foregroundMs` INTEGER NOT NULL, `savedMs` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`packageName`, `date`))"
            )
            connection.execSQL(
                "INSERT INTO `screen_time_records_new` (`packageName`, `date`, `foregroundMs`, `savedMs`) " +
                    "SELECT `packageName`, `date`, `foregroundMs`, `savedMs` FROM `screen_time_records` " +
                    "WHERE `id` IN (SELECT MAX(`id`) FROM `screen_time_records` GROUP BY `packageName`, `date`)"
            )
            connection.execSQL("DROP TABLE `screen_time_records`")
            connection.execSQL("ALTER TABLE `screen_time_records_new` RENAME TO `screen_time_records`")
        }
    }

    fun create(context: Context): QuestLogDatabase =
        Room.databaseBuilder(
            context.applicationContext,
            QuestLogDatabase::class.java,
            QuestLogDatabase.DB_NAME,
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()
}
