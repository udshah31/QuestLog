package com.questlog.data.repository

import com.questlog.data.local.dao.CurrencyDao
import com.questlog.data.local.entity.CurrencyBalance
import com.questlog.domain.model.PlayerStats
import com.questlog.util.TimeConversion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

class CurrencyRepository(private val dao: CurrencyDao) {

    suspend fun ensureInitialized() {
        if (dao.get() == null) {
            dao.upsert(CurrencyBalance(updatedAt = Clock.System.now().toEpochMilliseconds()))
        }
    }

    suspend fun addRewards(xpDelta: Long, goldDelta: Long) {
        dao.addRewards(xpDelta, goldDelta, Clock.System.now().toEpochMilliseconds())
    }

    fun observePlayerStats(todaySavedMs: Long = 0L): Flow<PlayerStats> =
        dao.observe().map { balance ->
            val b = balance ?: CurrencyBalance()
            val multiplier = TimeConversion.streakMultiplier(b.consecutiveDetoxDays)
            PlayerStats(
                level = TimeConversion.levelFromXp(b.xp),
                xp = b.xp,
                xpToNextLevel = TimeConversion.xpForLevel(TimeConversion.levelFromXp(b.xp) + 1),
                gold = b.gold,
                gems = b.gems,
                consecutiveDetoxDays = b.consecutiveDetoxDays,
                streakMultiplier = multiplier,
                todaySavedMs = todaySavedMs,
            )
        }
}
