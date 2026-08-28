package com.questlog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.questlog.data.local.entity.InventoryItem
import com.questlog.data.local.entity.ItemType
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addItem(item: InventoryItem)

    @Query("SELECT * FROM inventory_items WHERE type = :type ORDER BY acquiredAt DESC")
    fun getByType(type: ItemType): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items ORDER BY acquiredAt DESC")
    fun getAll(): Flow<List<InventoryItem>>

    @Query("SELECT EXISTS(SELECT 1 FROM inventory_items WHERE itemId = :itemId)")
    suspend fun isOwned(itemId: String): Boolean

    @Query("SELECT COUNT(*) FROM inventory_items WHERE type = 'BUILDING' AND acquiredAt >= :sinceMs")
    suspend fun countBuildingsAcquiredSince(sinceMs: Long): Int
}
