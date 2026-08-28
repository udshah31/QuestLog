package com.questlog.domain.usecase

import com.questlog.data.repository.CurrencyRepository
import com.questlog.data.repository.DailyQuestRepository
import com.questlog.data.repository.InventoryRepository
import com.questlog.data.repository.ScreenTimeRepository
import com.questlog.domain.quest.BUDGET_GUARDIAN_MAX_FLAGGED_MS
import com.questlog.domain.quest.CENTURY_SAVER_MIN_SAVED_MS
import com.questlog.domain.quest.DAWN_DISCIPLINE_END_HOUR
import com.questlog.domain.quest.DEEP_FOCUS_END_HOUR
import com.questlog.domain.quest.DEEP_FOCUS_START_HOUR
import com.questlog.domain.quest.DIGITAL_FASTING_MAX_MS
import com.questlog.domain.quest.DIGITAL_FASTING_PACKAGE
import com.questlog.domain.quest.FEED_FREEZE_MAX_MS
import com.questlog.domain.quest.FEED_FREEZE_PACKAGES
import com.questlog.domain.quest.MASTER_BUILDER_MIN_BUILDINGS
import com.questlog.domain.quest.QuestIds
import com.questlog.domain.quest.questsForDay
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Evaluates the day's active daily quests (see [questsForDay]) against real usage /
 * inventory data and auto-grants each quest's reward exactly once, the first time its
 * condition is satisfied. Runs on every detox tick (see [CalculateDetoxRewardsUseCase]).
 */
class EvaluateDailyQuestsUseCase(
    private val screenTimeRepo: ScreenTimeRepository,
    private val inventoryRepo: InventoryRepository,
    private val currencyRepo: CurrencyRepository,
    private val questRepo: DailyQuestRepository,
    private val flaggedPackages: Set<String>,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend operator fun invoke() {
        val now = clock.now()
        val today = now.toLocalDateTime(timeZone).date
        val todayKey = today.toString()
        val startOfDayMs = today.atStartOfDayIn(timeZone).toEpochMilliseconds()
        val nowMs = now.toEpochMilliseconds()

        val alreadyDone = questRepo.completedIds(todayKey).toSet()

        for (quest in questsForDay(today)) {
            if (quest.id in alreadyDone) continue
            if (!isComplete(quest.id, todayKey, startOfDayMs, nowMs, today)) continue
            if (questRepo.markCompleted(todayKey, quest.id)) {
                currencyRepo.addRewards(quest.xpReward, quest.goldReward)
            }
        }
    }

    private suspend fun isComplete(
        questId: String,
        todayKey: String,
        startOfDayMs: Long,
        nowMs: Long,
        today: LocalDate,
    ): Boolean = when (questId) {
        QuestIds.DIGITAL_FASTING ->
            screenTimeRepo.foregroundMsForPackageOnDate(DIGITAL_FASTING_PACKAGE, todayKey) <= DIGITAL_FASTING_MAX_MS

        QuestIds.DEEP_FOCUS_SHIELD -> {
            val windowEndMs = today.atTime(DEEP_FOCUS_END_HOUR, 0).toInstant(timeZone).toEpochMilliseconds()
            // Only decidable once the focus window has fully elapsed.
            if (nowMs < windowEndMs) {
                false
            } else {
                val windowStartMs = today.atTime(DEEP_FOCUS_START_HOUR, 0).toInstant(timeZone).toEpochMilliseconds()
                screenTimeRepo.flaggedForegroundInWindow(windowStartMs, windowEndMs, flaggedPackages) == 0L
            }
        }

        QuestIds.SANCTUARY_BUILDER ->
            inventoryRepo.buildingsAcquiredSince(startOfDayMs) > 0

        QuestIds.FEED_FREEZE ->
            FEED_FREEZE_PACKAGES.sumOf {
                screenTimeRepo.foregroundMsForPackageOnDate(it, todayKey)
            } <= FEED_FREEZE_MAX_MS

        QuestIds.CENTURY_SAVER -> {
            val balance = currencyRepo.currentBalance()
            balance != null && balance.rewardDate == todayKey &&
                balance.awardedSavedMsToday >= CENTURY_SAVER_MIN_SAVED_MS
        }

        QuestIds.BUDGET_GUARDIAN ->
            screenTimeRepo.totalForegroundMs(todayKey) <= BUDGET_GUARDIAN_MAX_FLAGGED_MS

        QuestIds.MASTER_BUILDER ->
            inventoryRepo.buildingsAcquiredSince(startOfDayMs) >= MASTER_BUILDER_MIN_BUILDINGS

        QuestIds.DAWN_DISCIPLINE -> {
            val windowEndMs = today.atTime(DAWN_DISCIPLINE_END_HOUR, 0).toInstant(timeZone).toEpochMilliseconds()
            // Only decidable once the pre-9am window has fully elapsed.
            if (nowMs < windowEndMs) {
                false
            } else {
                screenTimeRepo.flaggedForegroundInWindow(startOfDayMs, windowEndMs, flaggedPackages) == 0L
            }
        }

        else -> false
    }
}
