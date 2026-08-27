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

    fun create(context: Context): QuestLogDatabase =
        Room.databaseBuilder(
            context.applicationContext,
            QuestLogDatabase::class.java,
            QuestLogDatabase.DB_NAME,
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()
}
