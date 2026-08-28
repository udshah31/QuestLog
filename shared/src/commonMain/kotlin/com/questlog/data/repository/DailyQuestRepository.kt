package com.questlog.data.repository

import com.questlog.data.local.dao.QuestDao
import com.questlog.data.local.entity.QuestCompletion
import com.questlog.domain.model.DailyQuest
import com.questlog.domain.quest.questCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class DailyQuestRepository(private val dao: QuestDao) {

    private fun today(): String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return "${now.year}-${now.monthNumber.toString().padStart(2, '0')}-${now.dayOfMonth.toString().padStart(2, '0')}"
    }

    /** The catalog with each quest's `isCompleted` resolved against today's completion rows. */
    fun observeToday(): Flow<List<DailyQuest>> {
        val date = today()
        return dao.observeCompletedIds(date).map { completedIds ->
            val done = completedIds.toSet()
            questCatalog.map { it.copy(isCompleted = it.id in done) }
        }
    }

    suspend fun completedIds(date: String): List<String> = dao.completedIds(date)

    /** Records a completion; returns true only if this call was the one that inserted it. */
    suspend fun markCompleted(date: String, questId: String): Boolean =
        dao.insertIfAbsent(
            QuestCompletion(date, questId, Clock.System.now().toEpochMilliseconds()),
        ) != -1L
}
