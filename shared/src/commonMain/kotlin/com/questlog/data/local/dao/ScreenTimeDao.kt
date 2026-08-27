package com.questlog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.questlog.data.local.entity.ScreenTimeRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenTimeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: ScreenTimeRecord)

    @Query("SELECT * FROM screen_time_records WHERE date = :date ORDER BY savedMs DESC")
    fun getByDate(date: String): Flow<List<ScreenTimeRecord>>

    @Query("SELECT * FROM screen_time_records WHERE date >= :fromDate ORDER BY date DESC")
    fun getSince(fromDate: String): Flow<List<ScreenTimeRecord>>

    @Query("SELECT SUM(savedMs) FROM screen_time_records WHERE date = :date")
    suspend fun totalSavedMsForDate(date: String): Long
}
