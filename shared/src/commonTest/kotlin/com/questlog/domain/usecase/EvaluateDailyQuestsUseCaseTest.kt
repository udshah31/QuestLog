package com.questlog.domain.usecase

import com.questlog.data.local.dao.InventoryDao
import com.questlog.data.local.dao.QuestDao
import com.questlog.data.local.entity.InventoryItem
import com.questlog.data.local.entity.ItemType
import com.questlog.data.local.entity.QuestCompletion
import com.questlog.data.repository.CurrencyRepository
import com.questlog.data.repository.DailyQuestRepository
import com.questlog.data.repository.InventoryRepository
import com.questlog.data.repository.ScreenTimeRepository
import com.questlog.domain.platform.ScreenTimeTracker
import com.questlog.domain.quest.QuestIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val FIXED_DATE = LocalDate(2026, 8, 28)
private val UTC = TimeZone.UTC
private fun at(hour: Int): Instant = FIXED_DATE.atTime(hour, 0).toInstant(UTC)
private class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

private class FakeQuestDao : QuestDao {
    val completed = linkedSetOf<String>()
    private val flow = MutableStateFlow<List<String>>(emptyList())
    override suspend fun insertIfAbsent(completion: QuestCompletion): Long {
        val added = completed.add(completion.questId)
        flow.value = completed.toList()
        return if (added) 1L else -1L
    }
    override fun observeCompletedIds(date: String): Flow<List<String>> = flow
    override suspend fun completedIds(date: String): List<String> = completed.toList()
}

private class FakeInventoryDao(private val buildingsToday: Int = 0) : InventoryDao {
    override suspend fun addItem(item: InventoryItem) {}
    override fun getByType(type: ItemType): Flow<List<InventoryItem>> = MutableStateFlow(emptyList())
    override fun getAll(): Flow<List<InventoryItem>> = MutableStateFlow(emptyList())
    override suspend fun isOwned(itemId: String): Boolean = false
    override suspend fun countBuildingsAcquiredSince(sinceMs: Long): Int = buildingsToday
}

private class StubQuestScreenTimeRepo(
    private val instagramMs: Long = 0L,
    private val windowFlaggedMs: Long = 0L,
) : ScreenTimeRepository(FakeScreenTimeDao(), ScreenTimeTracker()) {
    override suspend fun foregroundMsForPackageOnDate(packageName: String, date: String): Long = instagramMs
    override suspend fun flaggedForegroundInWindow(
        startMs: Long,
        endMs: Long,
        flaggedPackages: Set<String>,
    ): Long = windowFlaggedMs
}

class EvaluateDailyQuestsUseCaseTest {

    private fun useCase(
        clock: Clock,
        screenTimeRepo: ScreenTimeRepository = StubQuestScreenTimeRepo(),
        inventoryDao: InventoryDao = FakeInventoryDao(),
        currencyDao: FakeCurrencyDao = FakeCurrencyDao(),
        questDao: FakeQuestDao = FakeQuestDao(),
    ): Triple<EvaluateDailyQuestsUseCase, FakeCurrencyDao, FakeQuestDao> {
        val uc = EvaluateDailyQuestsUseCase(
            screenTimeRepo = screenTimeRepo,
            inventoryRepo = InventoryRepository(inventoryDao),
            currencyRepo = CurrencyRepository(currencyDao),
            questRepo = DailyQuestRepository(questDao),
            flaggedPackages = setOf("com.instagram.android"),
            clock = clock,
            timeZone = UTC,
        )
        return Triple(uc, currencyDao, questDao)
    }

    @Test
    fun `digital fasting completes and grants its reward under the limit`() = runTest {
        val (uc, currency, quests) = useCase(
            clock = FixedClock(at(10)),
            screenTimeRepo = StubQuestScreenTimeRepo(instagramMs = 10 * 60_000L),
        )

        uc()

        assertTrue(QuestIds.DIGITAL_FASTING in quests.completed)
        assertEquals(150L, currency.balance.xp)
        assertEquals(30L, currency.balance.gold)
    }

    @Test
    fun `digital fasting does not complete over the limit`() = runTest {
        val (uc, currency, quests) = useCase(
            clock = FixedClock(at(10)),
            screenTimeRepo = StubQuestScreenTimeRepo(instagramMs = 20 * 60_000L),
        )

        uc()

        assertFalse(QuestIds.DIGITAL_FASTING in quests.completed)
        assertEquals(0L, currency.balance.xp)
    }

    @Test
    fun `deep focus shield stays incomplete before noon`() = runTest {
        val (uc, _, quests) = useCase(
            clock = FixedClock(at(10)), // window not over yet
            screenTimeRepo = StubQuestScreenTimeRepo(windowFlaggedMs = 0L),
        )

        uc()

        assertFalse(QuestIds.DEEP_FOCUS_SHIELD in quests.completed)
    }

    @Test
    fun `deep focus shield completes after noon when the window was clean`() = runTest {
        val (uc, currency, quests) = useCase(
            clock = FixedClock(at(13)),
            // Instagram over the limit so only Deep Focus Shield can complete here.
            screenTimeRepo = StubQuestScreenTimeRepo(instagramMs = 999 * 60_000L, windowFlaggedMs = 0L),
        )

        uc()

        assertTrue(QuestIds.DEEP_FOCUS_SHIELD in quests.completed)
        assertEquals(300L, currency.balance.xp)
    }

    @Test
    fun `deep focus shield fails after noon when the window had usage`() = runTest {
        val (uc, _, quests) = useCase(
            clock = FixedClock(at(13)),
            screenTimeRepo = StubQuestScreenTimeRepo(windowFlaggedMs = 5 * 60_000L),
        )

        uc()

        assertFalse(QuestIds.DEEP_FOCUS_SHIELD in quests.completed)
    }

    @Test
    fun `sanctuary builder completes when a building was constructed today`() = runTest {
        val (uc, currency, quests) = useCase(
            clock = FixedClock(at(10)), // before noon, so Deep Focus Shield can't resolve
            screenTimeRepo = StubQuestScreenTimeRepo(instagramMs = 999 * 60_000L), // Digital Fasting fails
            inventoryDao = FakeInventoryDao(buildingsToday = 1),
        )

        uc()

        assertTrue(QuestIds.SANCTUARY_BUILDER in quests.completed)
        assertEquals(200L, currency.balance.xp)
    }

    @Test
    fun `a completed quest's reward is granted only once across repeated runs`() = runTest {
        val (uc, currency, _) = useCase(
            clock = FixedClock(at(10)),
            screenTimeRepo = StubQuestScreenTimeRepo(instagramMs = 0L),
        )

        uc(); uc(); uc()

        assertEquals(150L, currency.balance.xp) // Digital Fasting, once
        assertEquals(30L, currency.balance.gold)
    }
}
