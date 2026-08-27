package com.questlog.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.questlog.data.local.converter.ItemTypeConverter
import com.questlog.data.local.dao.CurrencyDao
import com.questlog.data.local.dao.InventoryDao
import com.questlog.data.local.dao.ScreenTimeDao
import com.questlog.data.local.entity.CurrencyBalance
import com.questlog.data.local.entity.InventoryItem
import com.questlog.data.local.entity.ScreenTimeRecord

@Database(
    entities = [
        ScreenTimeRecord::class,
        CurrencyBalance::class,
        InventoryItem::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(ItemTypeConverter::class)
abstract class QuestLogDatabase : RoomDatabase() {
    abstract fun screenTimeDao(): ScreenTimeDao
    abstract fun currencyDao(): CurrencyDao
    abstract fun inventoryDao(): InventoryDao

    companion object {
        const val DB_NAME = "questlog.db"
    }
}
