package com.questlog.domain.usecase

import com.questlog.data.local.dao.QuestDao
import com.questlog.data.repository.BlocklistRepository
import com.questlog.data.repository.CurrencyRepository
import com.questlog.data.repository.DailySavedRepository
import com.questlog.domain.model.DaySaved
import com.questlog.domain.model.ProgressStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
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
    /** The local date, re-emitted at each midnight so an open screen rolls its week over. */
    private val dates: Flow<LocalDate> = localDates(clock, timeZone),
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<ProgressStats> = dates.distinctUntilChanged().flatMapLatest { today -> week(today) }

    private fun week(today: LocalDate): Flow<ProgressStats> {
        val windowStart = today.minus(6, DateTimeUnit.DAY)
        return combine(
            currencyRepo.observePlayerStats(),
            dailySavedRepo.observeSince(windowStart.toString()),
            dailySavedRepo.observeBestMsBefore(today.toString()),
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

/**
 * Emits today's local date now and again just after each local midnight.
 * ponytail: a timezone change mid-day is picked up at the next midnight tick, not instantly.
 */
fun localDates(clock: Clock, timeZone: TimeZone): Flow<LocalDate> = flow {
    while (true) {
        val now = clock.now()
        val today = now.toLocalDateTime(timeZone).date
        emit(today)
        val nextMidnight = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
        delay((nextMidnight - now).inWholeMilliseconds + 1_000L)
    }
}
