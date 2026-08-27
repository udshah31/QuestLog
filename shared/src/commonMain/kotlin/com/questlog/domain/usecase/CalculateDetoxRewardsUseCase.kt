package com.questlog.domain.usecase

import com.questlog.data.repository.CurrencyRepository
import com.questlog.data.repository.ScreenTimeRepository
import com.questlog.domain.model.DetoxMetrics
import com.questlog.util.TimeConversion
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * Orchestrates the core detox loop:
 * 1. Fetch today's usage via ScreenTimeRepository (which delegates to ScreenTimeTracker)
 * 2. Compute XP and gold deltas using TimeConversion
 * 3. Persist rewards to CurrencyRepository
 * 4. Return a DetoxMetrics snapshot for the UI layer
 */
class CalculateDetoxRewardsUseCase(
    private val screenTimeRepo: ScreenTimeRepository,
    private val currencyRepo: CurrencyRepository,
    private val flaggedPackages: Set<String>,
) {
    suspend operator fun invoke(): DetoxMetrics {
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val today = now.toLocalDateTime(tz).date
        val startOfDay = today.atStartOfDayIn(tz).toEpochMilliseconds()

        // 1. Fetch & persist screen-time data
        val savedMs = screenTimeRepo.fetchAndPersistToday(flaggedPackages, startOfDay)

        // 2. Retrieve current stats for streak multiplier
        val balance = currencyRepo.observePlayerStats(savedMs)
        var stats = balance.first() // snapshot

        val multiplier = TimeConversion.streakMultiplier(stats.consecutiveDetoxDays)
        val xpDelta = TimeConversion.xpEarned(savedMs, multiplier)
        val goldDelta = TimeConversion.goldEarned(savedMs, multiplier)

        // 3. Persist rewards
        currencyRepo.addRewards(xpDelta, goldDelta)

        // 4. Re-read updated stats
        stats = currencyRepo.observePlayerStats(savedMs).first()

        return DetoxMetrics(
            timeSavedMs = savedMs,
            xpEarned = xpDelta,
            goldEarned = goldDelta,
            currentLevel = stats.level,
            xpProgress = TimeConversion.xpProgress(stats.xp),
            consecutiveDetoxDays = stats.consecutiveDetoxDays,
            streakMultiplier = multiplier,
        )
    }
}
