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

    @Query("SELECT * FROM screen_time_records WHERE date = :date ORDER BY foregroundMs DESC")
    fun getByDate(date: String): Flow<List<ScreenTimeRecord>>

    @Query("SELECT * FROM screen_time_records WHERE date >= :fromDate ORDER BY date DESC")
    fun getSince(fromDate: String): Flow<List<ScreenTimeRecord>>

    @Query("SELECT COALESCE(SUM(foregroundMs), 0) FROM screen_time_records WHERE date = :date")
    suspend fun totalForegroundMsForDate(date: String): Long

    /** Every app that had foreground time recorded on [date] — i.e. was blocked at some tick. */
    @Query("SELECT DISTINCT packageName FROM screen_time_records WHERE date = :date")
    suspend fun packagesForDate(date: String): List<String>

    @Query("SELECT COALESCE(SUM(foregroundMs), 0) FROM screen_time_records WHERE date = :date AND packageName = :packageName")
    suspend fun foregroundMsForPackageOnDate(packageName: String, date: String): Long
}
