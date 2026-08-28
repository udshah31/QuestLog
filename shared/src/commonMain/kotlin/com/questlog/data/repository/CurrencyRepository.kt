package com.questlog.data.repository

import com.questlog.data.local.dao.CurrencyDao
import com.questlog.data.local.entity.CurrencyBalance
import com.questlog.domain.model.PlayerStats
import com.questlog.util.StreakFreeze
import com.questlog.util.TimeConversion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class CurrencyRepository(private val dao: CurrencyDao) {

    /**
     * Guarantees the single balance row (id = 1) exists. Safe to call concurrently and
     * repeatedly: [CurrencyDao.insertIfAbsent] ignores the insert when the row is present,
     * so an already-funded balance is never reset to zero.
     */
    suspend fun ensureInitialized() {
        dao.insertIfAbsent(CurrencyBalance(updatedAt = Clock.System.now().toEpochMilliseconds()))
    }

    /** Raw current balance row, or null if it has never been written. */
    suspend fun currentBalance(): CurrencyBalance? = dao.get()

    suspend fun addRewards(xpDelta: Long, goldDelta: Long) {
        // Room's addRewards is an UPDATE WHERE id = 1 — a no-op on a fresh install where the
        // row was never seeded. Ensure it exists first so rewards are never silently lost.
        ensureInitialized()
        dao.addRewards(xpDelta, goldDelta, Clock.System.now().toEpochMilliseconds())
    }

    /** Records the high-water mark of saved screen-time already converted to rewards for [date]. */
    suspend fun setDailyAward(date: String, awardedSavedMs: Long) {
        ensureInitialized()
        dao.updateDailyAward(date, awardedSavedMs, Clock.System.now().toEpochMilliseconds())
    }

    /** Sets the consecutive-detox-day count (0 = streak broken). */
    suspend fun setStreak(days: Int) {
        ensureInitialized()
        dao.setStreak(days.coerceAtLeast(0), Clock.System.now().toEpochMilliseconds())
    }

    /** Records that the streak-freeze charge was spent on [date] (ISO). */
    suspend fun setStreakFreezeUsed(date: String) {
        ensureInitialized()
        dao.setStreakFreezeUsed(date, Clock.System.now().toEpochMilliseconds())
    }

    fun observePlayerStats(): Flow<PlayerStats> =
        dao.observe().map { balance ->
            val b = balance ?: CurrencyBalance()
            val multiplier = TimeConversion.streakMultiplier(b.consecutiveDetoxDays)
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            PlayerStats(
                level = TimeConversion.levelFromXp(b.xp),
                xp = b.xp,
                xpToNextLevel = TimeConversion.xpForLevel(TimeConversion.levelFromXp(b.xp) + 1),
                gold = b.gold,
                gems = b.gems,
                consecutiveDetoxDays = b.consecutiveDetoxDays,
                streakMultiplier = multiplier,
                todaySavedMs = b.awardedSavedMsToday,
                streakFreezeReady = StreakFreeze.isRechargedOn(b.streakFreezeLastUsed, today),
            )
        }
}
