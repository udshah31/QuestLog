package com.questlog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.questlog.data.local.entity.BlockedAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlocklistDao {
    @Query("SELECT * FROM blocked_app ORDER BY packageName")
    fun observeAll(): Flow<List<BlockedAppEntity>>

    @Query("SELECT * FROM blocked_app ORDER BY packageName")
    suspend fun getAll(): List<BlockedAppEntity>

    @Query("SELECT * FROM blocked_app WHERE packageName = :packageName")
    suspend fun get(packageName: String): BlockedAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(app: BlockedAppEntity)

    @Query("DELETE FROM blocked_app WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}
