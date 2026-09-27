package com.questlog.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import com.questlog.data.local.converter.ItemTypeConverter
import com.questlog.data.local.dao.BlocklistDao
import com.questlog.data.local.dao.CurrencyDao
import com.questlog.data.local.dao.InventoryDao
import com.questlog.data.local.dao.QuestDao
import com.questlog.data.local.dao.ScreenTimeDao
import com.questlog.data.local.entity.BlockedAppEntity
import com.questlog.data.local.entity.CurrencyBalance
import com.questlog.data.local.entity.InventoryItem
import com.questlog.data.local.entity.QuestCompletion
import com.questlog.data.local.entity.ScreenTimeRecord
import com.questlog.data.local.entity.MindfulUnlockEntity
import com.questlog.data.local.entity.DailySaved
import com.questlog.data.local.dao.DailySavedDao
import com.questlog.data.local.dao.MindfulUnlockDao

@Database(
    entities = [
        ScreenTimeRecord::class,
        CurrencyBalance::class,
        InventoryItem::class,
        QuestCompletion::class,
        BlockedAppEntity::class,
        MindfulUnlockEntity::class,
        DailySaved::class,
    ],
    version = 11,
    exportSchema = true,
)
@ConstructedBy(QuestLogDatabaseConstructor::class)
@TypeConverters(ItemTypeConverter::class)
abstract class QuestLogDatabase : RoomDatabase() {
    abstract fun screenTimeDao(): ScreenTimeDao
    abstract fun currencyDao(): CurrencyDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun questDao(): QuestDao
    abstract fun blocklistDao(): BlocklistDao
    abstract fun mindfulUnlockDao(): MindfulUnlockDao
    abstract fun dailySavedDao(): DailySavedDao

    companion object {
        const val DB_NAME = "questlog.db"
    }
}

/** Room generates the per-platform `actual` implementation of this object via KSP. */
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object QuestLogDatabaseConstructor : RoomDatabaseConstructor<QuestLogDatabase> {
    override fun initialize(): QuestLogDatabase
}
