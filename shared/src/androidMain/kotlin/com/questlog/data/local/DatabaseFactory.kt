package com.questlog.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

object DatabaseFactory {
    fun create(context: Context): QuestLogDatabase =
        Room.databaseBuilder(
            context.applicationContext,
            QuestLogDatabase::class.java,
            QuestLogDatabase.DB_NAME,
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(*questLogMigrations)
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()
}
