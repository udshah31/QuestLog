package com.questlog.data.repository

import com.questlog.data.local.dao.QuestDao
import com.questlog.data.local.entity.QuestCompletion
import com.questlog.domain.quest.QuestIds
import com.questlog.domain.quest.questsForDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 2026-01-03 is an epoch day ≡ 0 (mod 8), so the rotation window is catalog[0..2]:
// Digital Fasting, Sanctuary Builder, Deep Focus Shield.
private val FIXED_DATE = LocalDate(2026, 1, 3)
private val FIXED_INSTANT: Instant = FIXED_DATE.atStartOfDayIn(TimeZone.UTC)
private class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

private class FakeQuestDao(completed: List<String> = emptyList()) : QuestDao {
    private val flow = MutableStateFlow(completed)
    override suspend fun insertIfAbsent(completion: QuestCompletion): Long = 1L
    override fun observeCompletedIds(date: String): Flow<List<String>> = flow
    override suspend fun completedIds(date: String): List<String> = flow.value
}

class DailyQuestRepositoryTest {

    private fun repo(completed: List<String> = emptyList()) =
        DailyQuestRepository(FakeQuestDao(completed), FixedClock(FIXED_INSTANT), TimeZone.UTC)

    @Test
    fun `observeToday returns exactly the day's rotated quests`() = runTest {
        val quests = repo().observeToday().first()

        assertEquals(questsForDay(FIXED_DATE).map { it.id }, quests.map { it.id })
    }

    @Test
    fun `observeToday resolves completion for a quest in today's window`() = runTest {
        val quests = repo(completed = listOf(QuestIds.SANCTUARY_BUILDER)).observeToday().first()

        assertTrue(quests.single { it.id == QuestIds.SANCTUARY_BUILDER }.isCompleted)
        assertFalse(quests.single { it.id == QuestIds.DIGITAL_FASTING }.isCompleted)
    }

    @Test
    fun `observeToday marks nothing complete when there are no completion rows`() = runTest {
        val quests = repo().observeToday().first()

        assertTrue(quests.none { it.isCompleted })
    }
}
