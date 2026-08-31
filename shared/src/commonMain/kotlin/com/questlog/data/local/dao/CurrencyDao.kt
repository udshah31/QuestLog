package com.questlog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.questlog.data.local.entity.CurrencyBalance
import kotlinx.coroutines.flow.Flow

@Dao
interface CurrencyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(balance: CurrencyBalance)

    /** Seeds the single balance row (id = 1) only if it does not already exist. Never overwrites. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(balance: CurrencyBalance)

    @Query("SELECT * FROM currency_balance WHERE id = 1")
    fun observe(): Flow<CurrencyBalance?>

    @Query("SELECT * FROM currency_balance WHERE id = 1")
    suspend fun get(): CurrencyBalance?

    @Query("""
        UPDATE currency_balance
        SET xp = xp + :xpDelta, gold = gold + :goldDelta,
            updatedAt = :now
        WHERE id = 1
    """)
    suspend fun addRewards(xpDelta: Long, goldDelta: Long, now: Long)

    @Query("""
        UPDATE currency_balance
        SET rewardDate = :date, awardedSavedMsToday = :awardedSavedMs, updatedAt = :now
        WHERE id = 1
    """)
    suspend fun updateDailyAward(date: String, awardedSavedMs: Long, now: Long)

    @Query("UPDATE currency_balance SET consecutiveDetoxDays = :days, updatedAt = :now WHERE id = 1")
    suspend fun setStreak(days: Int, now: Long)

    @Query("UPDATE currency_balance SET lifetimeSavedMs = lifetimeSavedMs + :deltaMs, updatedAt = :now WHERE id = 1")
    suspend fun addLifetimeSaved(deltaMs: Long, now: Long)

    @Query("UPDATE currency_balance SET streakFreezeLastUsed = :date, updatedAt = :now WHERE id = 1")
    suspend fun setStreakFreezeUsed(date: String, now: Long)
}
