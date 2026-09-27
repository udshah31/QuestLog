package com.questlog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.questlog.data.local.entity.DailySaved
import kotlinx.coroutines.flow.Flow

@Dao
interface DailySavedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: DailySaved)

    @Query("SELECT * FROM daily_saved WHERE date >= :fromDate ORDER BY date ASC")
    fun observeSince(fromDate: String): Flow<List<DailySaved>>

    /** Best finished day before [date] — a stray row for today (clock moved back) never counts. */
    @Query("SELECT MAX(savedMs) FROM daily_saved WHERE date < :date")
    fun observeBestMsBefore(date: String): Flow<Long?>
}
