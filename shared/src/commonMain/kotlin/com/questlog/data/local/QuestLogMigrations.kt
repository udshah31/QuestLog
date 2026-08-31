package com.questlog.data.local

import androidx.room.RoomDatabase
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

/** v5: drop the unused currency_balance.currentLevel column (level is derived from xp). */
internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `currency_balance` DROP COLUMN `currentLevel`")
    }
}

/** v6: drop screen_time_records.savedMs — saved time is now computed for the day as a whole. */
internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `screen_time_records` DROP COLUMN `savedMs`")
    }
}

/** v7: streak-freeze (Pro perk) — records when the weekly charge was last spent. */
internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `currency_balance` ADD COLUMN `streakFreezeLastUsed` TEXT NOT NULL DEFAULT ''")
    }
}

/** v8: user-editable distraction list. Seeds the seven historical defaults. */
internal val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `blocked_app` (" +
                "`packageName` TEXT NOT NULL, `dailyLimitMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`packageName`))"
        )
        val seed = listOf(
            "com.instagram.android", "com.zhiliaoapp.musically", "com.snapchat.android",
            "com.twitter.android", "com.reddit.frontpage", "com.google.android.youtube",
            "com.facebook.katana",
        )
        for (pkg in seed) {
            connection.execSQL(
                "INSERT OR IGNORE INTO `blocked_app` (`packageName`, `dailyLimitMs`) VALUES ('$pkg', 0)"
            )
        }
    }
}

/** v9: all-time saved screen-time total on the currency row. */
internal val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `currency_balance` ADD COLUMN `lifetimeSavedMs` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Seeds `blocked_app` on a fresh database (fresh installs never run migrations).
 * Uses the live [com.questlog.domain.model.defaultFlaggedPackages] — unlike a
 * migration, this always represents "the current default", which is the right
 * behaviour for a new user.
 */
val questLogSeedCallback = object : RoomDatabase.Callback() {
    override fun onCreate(connection: SQLiteConnection) {
        for (pkg in com.questlog.domain.model.defaultFlaggedPackages) {
            connection.execSQL(
                "INSERT OR IGNORE INTO `blocked_app` (`packageName`, `dailyLimitMs`) VALUES ('$pkg', 0)"
            )
        }
    }
}

/** Every migration the app database ships, in order. */
internal val questLogMigrations: Array<Migration> =
    arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
    )
