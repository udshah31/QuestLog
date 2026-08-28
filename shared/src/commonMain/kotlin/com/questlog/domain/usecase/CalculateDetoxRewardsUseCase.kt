package com.questlog.domain.usecase

import com.questlog.data.local.entity.CurrencyBalance
import com.questlog.data.repository.CurrencyRepository
import com.questlog.data.repository.ScreenTimeRepository
import com.questlog.domain.model.DetoxMetrics
import com.questlog.util.StreakFreeze
import com.questlog.util.TimeConversion
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

/**
 * Orchestrates the core detox loop:
 * 1. Fetch today's usage via ScreenTimeRepository (which delegates to ScreenTimeTracker)
 * 2. Compute XP and gold for the *incremental* saved-time since the last run today
 * 3. Persist the delta to CurrencyRepository and advance today's high-water mark
 * 4. Return a DetoxMetrics snapshot for the UI layer
 *
 * The daily reward is idempotent: re-running this (manual refresh, periodic monitor)
 * only ever grants the increase in saved time, never the whole daily total again.
 *
 * On a day rollover it also advances the detox streak: each calendar day since the last
 * run whose flagged-app foreground time stayed within [dailyFlaggedBudgetMs] adds to
 * [com.questlog.data.local.entity.CurrencyBalance.consecutiveDetoxDays]; the first day
 * over budget resets it.
 */
class CalculateDetoxRewardsUseCase(
    private val screenTimeRepo: ScreenTimeRepository,
    private val currencyRepo: CurrencyRepository,
    private val flaggedPackages: Set<String>,
    private val dailyFlaggedBudgetMs: Long = 60 * 60_000L,
    private val evaluateDailyQuests: suspend () -> Unit = {},
    private val isPremium: () -> Boolean = { false },
) {
    suspend operator fun invoke(): DetoxMetrics {
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val today = now.toLocalDateTime(tz).date
        val todayKey = today.toString() // ISO-8601 "yyyy-MM-dd"
        val startOfDay = today.atStartOfDayIn(tz).toEpochMilliseconds()

        // 1. Fetch & persist screen-time data
        val savedMs = screenTimeRepo.fetchAndPersistToday(flaggedPackages, startOfDay)

        // 2. Only reward the *increase* over what today already paid out. Saved time can
        //    move up or down through the day; we never claw rewards back, so track a
        //    high-water mark and grant the difference.
        val balance = currencyRepo.currentBalance()
        val alreadyRewardedMs =
            if (balance?.rewardDate == todayKey) balance.awardedSavedMsToday else 0L
        val cumulativeSavedMs = maxOf(savedMs, alreadyRewardedMs)

        val premium = isPremium()

        // Advance the streak once per day, the first time we run after midnight.
        var streak = balance?.consecutiveDetoxDays ?: 0
        val lastDay = balance?.rewardDate
        if (!lastDay.isNullOrEmpty() && lastDay != todayKey) {
            streak = evaluateStreak(lastDay, today, streak, balance, premium)
            currencyRepo.setStreak(streak)
        }

        val premiumMultiplier = if (premium) PREMIUM_MULTIPLIER else 1f
        val multiplier = TimeConversion.streakMultiplier(streak) * premiumMultiplier
        val xpDelta = TimeConversion.xpEarned(cumulativeSavedMs, multiplier) -
            TimeConversion.xpEarned(alreadyRewardedMs, multiplier)
        val goldDelta = TimeConversion.goldEarned(cumulativeSavedMs, multiplier) -
            TimeConversion.goldEarned(alreadyRewardedMs, multiplier)

        // 3. Persist the delta and advance today's high-water mark
        if (xpDelta != 0L || goldDelta != 0L) {
            currencyRepo.addRewards(xpDelta, goldDelta)
        }
        currencyRepo.setDailyAward(todayKey, cumulativeSavedMs)

        // 4. Evaluate daily quests against the freshly-persisted data (auto-grants rewards)
        evaluateDailyQuests()

        // 5. Re-read updated stats
        val stats = currencyRepo.observePlayerStats().first()

        return DetoxMetrics(
            timeSavedMs = savedMs,
            xpEarned = xpDelta,
            goldEarned = goldDelta,
            currentLevel = stats.level,
            xpProgress = TimeConversion.xpProgress(stats.xp),
            consecutiveDetoxDays = stats.consecutiveDetoxDays,
            streakMultiplier = TimeConversion.streakMultiplier(streak),
        )
    }

    /**
     * Streak after the rollover from [lastDayKey] to [today]. Every calendar day in
     * `[lastDayKey, today)` that stayed within budget adds one. A day over budget resets
     * the streak — unless [premium], there is a positive streak to rescue, and their weekly
     * freeze charge is available, in which case the missed day is skipped and the charge is
     * spent. Days with no records (phone-free) always count as within budget.
     */
    private suspend fun evaluateStreak(
        lastDayKey: String,
        today: LocalDate,
        currentStreak: Int,
        balance: CurrencyBalance?,
        premium: Boolean,
    ): Int {
        val lastDay = runCatching { LocalDate.parse(lastDayKey) }.getOrNull() ?: return currentStreak
        val gapDays = lastDay.daysUntil(today)
        if (gapDays <= 0) return currentStreak
        val phoneFreeGapDays = gapDays - 1 // days strictly between lastDay and today have no records

        if (screenTimeRepo.totalForegroundMs(lastDayKey) <= dailyFlaggedBudgetMs) {
            return currentStreak + gapDays
        }
        // lastDay was over budget — the streak would reset.
        if (currentStreak > 0 && premium &&
            StreakFreeze.isRechargedOn(balance?.streakFreezeLastUsed.orEmpty(), today)
        ) {
            currencyRepo.setStreakFreezeUsed(today.toString())
            return currentStreak + phoneFreeGapDays
        }
        return phoneFreeGapDays
    }

    private companion object {
        const val PREMIUM_MULTIPLIER = 2f
    }
}
