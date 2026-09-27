package com.questlog.domain.usecase

import com.questlog.data.local.dao.QuestDao
import com.questlog.data.repository.BlocklistRepository
import com.questlog.data.repository.CurrencyRepository
import com.questlog.data.repository.DailySavedRepository
import com.questlog.domain.model.DaySaved
import com.questlog.domain.model.ProgressStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class GetProgressStatsUseCase(
    private val currencyRepo: CurrencyRepository,
    private val dailySavedRepo: DailySavedRepository,
    private val questDao: QuestDao,
    private val blocklistRepo: BlocklistRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    operator fun invoke(): Flow<ProgressStats> {
        val today = clock.now().toLocalDateTime(timeZone).date
        val windowStart = today.minus(6, DateTimeUnit.DAY)
        return combine(
            currencyRepo.observePlayerStats(),
            dailySavedRepo.observeSince(windowStart.toString()),
            dailySavedRepo.observeBestMs(),
            questDao.observeLifetimeCompletedCount(),
            blocklistRepo.observeBlockedApps(),
        ) { stats, history, bestHistorical, questCount, blocked ->
            val byDate = history.mapNotNull { row ->
                runCatching { LocalDate.parse(row.date) }.getOrNull()?.let { it to row.savedMs }
            }.toMap()
            val week = (0..6).map { i ->
                val d = windowStart.plus(i, DateTimeUnit.DAY)
                val isToday = d == today
                // Today is live; a daily_saved row for today (should never exist) is ignored.
                DaySaved(d, if (isToday) stats.todaySavedMs else (byDate[d] ?: 0L), isToday)
            }
            ProgressStats(
                streakDays = stats.consecutiveDetoxDays,
                reclaimedAllTimeMs = stats.lifetimeSavedMs,
                bestDayMs = maxOf(bestHistorical ?: 0L, stats.todaySavedMs),
                appsGuarded = blocked.size,
                questsCleared = questCount,
                level = stats.level,
                xp = stats.xp,
                xpToNextLevel = stats.xpToNextLevel,
                last7Days = week,
            )
        }
    }
}
