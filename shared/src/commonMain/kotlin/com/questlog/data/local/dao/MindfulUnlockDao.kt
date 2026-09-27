package com.questlog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.questlog.data.local.entity.MindfulUnlockEntity

data class PackageGrace(val packageName: String, val graceMs: Long)

@Dao
interface MindfulUnlockDao {
    @Insert
    suspend fun insert(row: MindfulUnlockEntity): Long

    @Query("SELECT packageName, SUM(graceMs) AS graceMs FROM mindful_unlock WHERE date = :date GROUP BY packageName")
    suspend fun graceByPackageForDate(date: String): List<PackageGrace>

    @Query("SELECT COUNT(*) FROM mindful_unlock WHERE date = :date")
    suspend fun countForDate(date: String): Int
}
