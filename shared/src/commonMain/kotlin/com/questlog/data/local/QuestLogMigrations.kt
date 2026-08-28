package com.questlog.data.local

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** v2: track how much of today's "saved" screen-time has already been converted to rewards. */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
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
internal val MIGRATION_2_3 = object : Migration(2, 3) {
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

/** v4: daily quest completion tracking. */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `quest_completions` (" +
                "`date` TEXT NOT NULL, `questId` TEXT NOT NULL, `completedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`date`, `questId`))"
        )
    }
}

/** Every migration the app database ships, in order. */
internal val questLogMigrations: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
