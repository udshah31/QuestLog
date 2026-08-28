package com.questlog.data.repository

import com.questlog.data.local.dao.QuestDao
import com.questlog.data.local.entity.QuestCompletion
import com.questlog.domain.model.DailyQuest
import com.questlog.domain.quest.questsForDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class DailyQuestRepository(
    private val dao: QuestDao,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

    private fun todayDate(): LocalDate = clock.now().toLocalDateTime(timeZone).date

    /** Today's active quests, each `isCompleted` resolved against today's completion rows. */
    fun observeToday(): Flow<List<DailyQuest>> {
        val date = todayDate()
        return dao.observeCompletedIds(date.toString()).map { completedIds ->
            val done = completedIds.toSet()
            questsForDay(date).map { it.copy(isCompleted = it.id in done) }
        }
    }

    suspend fun completedIds(date: String): List<String> = dao.completedIds(date)

    /** Records a completion; returns true only if this call was the one that inserted it. */
    suspend fun markCompleted(date: String, questId: String): Boolean =
        dao.insertIfAbsent(
            QuestCompletion(date, questId, clock.now().toEpochMilliseconds()),
        ) != -1L
}
