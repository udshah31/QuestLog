package com.questlog.data.repository

import com.questlog.data.local.dao.QuestDao
import com.questlog.data.local.entity.QuestCompletion
import com.questlog.domain.quest.QuestIds
import com.questlog.domain.quest.questCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeQuestDao(completed: List<String> = emptyList()) : QuestDao {
    private val flow = MutableStateFlow(completed)
    override suspend fun insertIfAbsent(completion: QuestCompletion): Long = 1L
    override fun observeCompletedIds(date: String): Flow<List<String>> = flow
    override suspend fun completedIds(date: String): List<String> = flow.value
}

class DailyQuestRepositoryTest {

    @Test
    fun `observeToday returns the whole catalog with completion resolved`() = runTest {
        val repo = DailyQuestRepository(FakeQuestDao(completed = listOf(QuestIds.SANCTUARY_BUILDER)))

        val quests = repo.observeToday().first()

        assertEquals(questCatalog.map { it.id }, quests.map { it.id })
        assertTrue(quests.single { it.id == QuestIds.SANCTUARY_BUILDER }.isCompleted)
        assertFalse(quests.single { it.id == QuestIds.DIGITAL_FASTING }.isCompleted)
    }

    @Test
    fun `observeToday marks nothing complete when there are no completion rows`() = runTest {
        val repo = DailyQuestRepository(FakeQuestDao())

        val quests = repo.observeToday().first()

        assertTrue(quests.none { it.isCompleted })
    }
}
