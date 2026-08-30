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
import com.questlog.domain.model.BlockedApp
import com.questlog.domain.platform.ScreenTimeTracker
import com.questlog.domain.quest.QuestIds
import com.questlog.domain.quest.questsForDay
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

private val UTC = TimeZone.UTC

private fun LocalDate.plusDays(): LocalDate = LocalDate.fromEpochDays(toEpochDays() + 1)

/** The first date on/after 2026-01-01 whose rotation window is exactly [ids], in order. */
private fun dateWithWindow(vararg ids: String): LocalDate {
    var d = LocalDate(2026, 1, 1)
    repeat(questsForDay(d).size * 8) {
        if (questsForDay(d).map { it.id } == ids.toList()) return d
        d = d.plusDays()
    }
    error("no date near 2026-01-01 has rotation window ${ids.toList()}")
}

// The three original quests share a window on this date.
private val FIXED_DATE = dateWithWindow(
    QuestIds.DIGITAL_FASTING, QuestIds.SANCTUARY_BUILDER, QuestIds.DEEP_FOCUS_SHIELD,
)

private fun at(hour: Int, date: LocalDate = FIXED_DATE): Instant =
    date.atTime(hour, 0).toInstant(UTC)

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

private const val INSTAGRAM = "com.instagram.android"
private const val TIKTOK = "com.zhiliaoapp.musically"
private const val X_APP = "com.twitter.android"

private class StubQuestScreenTimeRepo(
    private val instagramMs: Long = 0L,
    private val windowFlaggedMs: Long = 0L,
    private val perPackageMs: Map<String, Long> = emptyMap(),
    private val totalFlaggedMs: Long = 0L,
) : ScreenTimeRepository(FakeScreenTimeDao(), ScreenTimeTracker()) {
    override suspend fun foregroundMsForPackageOnDate(packageName: String, date: String): Long =
        perPackageMs[packageName] ?: if (packageName == INSTAGRAM) instagramMs else 0L
    override suspend fun totalForegroundMs(date: String): Long = totalFlaggedMs
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
            questRepo = DailyQuestRepository(questDao, clock, UTC),
            blockedApps = { listOf(BlockedApp(INSTAGRAM, 0L)) },
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

    // ── rotating pool: the five extra quests ─────────────────────────────────

    @Test
    fun `feed freeze completes when the three feeds stay under ten minutes combined`() = runTest {
        val date = dateWithWindow(
            QuestIds.FEED_FREEZE, QuestIds.CENTURY_SAVER, QuestIds.BUDGET_GUARDIAN,
        )
        val (uc, currency, quests) = useCase(
            clock = FixedClock(at(10, date)),
            screenTimeRepo = StubQuestScreenTimeRepo(
                perPackageMs = mapOf(INSTAGRAM to 3 * 60_000L, TIKTOK to 2 * 60_000L, X_APP to 1 * 60_000L),
                totalFlaggedMs = 40 * 60_000L, // Budget Guardian fails
            ),
        )

        uc()

        assertTrue(QuestIds.FEED_FREEZE in quests.completed)
        assertFalse(QuestIds.BUDGET_GUARDIAN in quests.completed)
        assertEquals(200L, currency.balance.xp)
        assertEquals(50L, currency.balance.gold)
    }

    @Test
    fun `feed freeze does not complete when the feeds go over the combined limit`() = runTest {
        val date = dateWithWindow(
            QuestIds.FEED_FREEZE, QuestIds.CENTURY_SAVER, QuestIds.BUDGET_GUARDIAN,
        )
        val (uc, _, quests) = useCase(
            clock = FixedClock(at(10, date)),
            screenTimeRepo = StubQuestScreenTimeRepo(
                perPackageMs = mapOf(INSTAGRAM to 6 * 60_000L, TIKTOK to 5 * 60_000L),
                totalFlaggedMs = 40 * 60_000L,
            ),
        )

        uc()

        assertFalse(QuestIds.FEED_FREEZE in quests.completed)
    }

    @Test
    fun `century saver completes once 60 minutes of saved time is banked today`() = runTest {
        val date = dateWithWindow(
            QuestIds.CENTURY_SAVER, QuestIds.BUDGET_GUARDIAN, QuestIds.MASTER_BUILDER,
        )
        val currencyDao = FakeCurrencyDao().apply {
            balance = balance.copy(rewardDate = date.toString(), awardedSavedMsToday = 70 * 60_000L)
        }
        val (uc, currency, quests) = useCase(
            clock = FixedClock(at(10, date)),
            screenTimeRepo = StubQuestScreenTimeRepo(totalFlaggedMs = 40 * 60_000L), // Budget Guardian fails
            currencyDao = currencyDao,
        )

        uc()

        assertTrue(QuestIds.CENTURY_SAVER in quests.completed)
        assertEquals(200L, currency.balance.xp)
        assertEquals(40L, currency.balance.gold)
    }

    @Test
    fun `century saver does not complete on yesterday's saved-time high-water mark`() = runTest {
        val date = dateWithWindow(
            QuestIds.CENTURY_SAVER, QuestIds.BUDGET_GUARDIAN, QuestIds.MASTER_BUILDER,
        )
        val currencyDao = FakeCurrencyDao().apply {
            // plenty banked, but stamped for a different day
            balance = balance.copy(rewardDate = date.plusDays().toString(), awardedSavedMsToday = 99 * 60_000L)
        }
        val (uc, _, quests) = useCase(
            clock = FixedClock(at(10, date)),
            screenTimeRepo = StubQuestScreenTimeRepo(totalFlaggedMs = 40 * 60_000L),
            currencyDao = currencyDao,
        )

        uc()

        assertFalse(QuestIds.CENTURY_SAVER in quests.completed)
    }

    @Test
    fun `budget guardian completes when total distraction time stays under 30 minutes`() = runTest {
        val date = dateWithWindow(
            QuestIds.BUDGET_GUARDIAN, QuestIds.MASTER_BUILDER, QuestIds.DAWN_DISCIPLINE,
        )
        val (uc, currency, quests) = useCase(
            clock = FixedClock(at(8, date)), // before 9am so Dawn Discipline can't resolve
            screenTimeRepo = StubQuestScreenTimeRepo(totalFlaggedMs = 20 * 60_000L),
        )

        uc()

        assertTrue(QuestIds.BUDGET_GUARDIAN in quests.completed)
        assertEquals(250L, currency.balance.xp)
        assertEquals(60L, currency.balance.gold)
    }

    @Test
    fun `master builder completes on the second building of the day`() = runTest {
        val date = dateWithWindow(
            QuestIds.MASTER_BUILDER, QuestIds.DAWN_DISCIPLINE, QuestIds.DIGITAL_FASTING,
        )
        val (uc, currency, quests) = useCase(
            clock = FixedClock(at(8, date)), // before 9am so Dawn Discipline can't resolve
            screenTimeRepo = StubQuestScreenTimeRepo(instagramMs = 20 * 60_000L), // Digital Fasting fails
            inventoryDao = FakeInventoryDao(buildingsToday = 2),
        )

        uc()

        assertTrue(QuestIds.MASTER_BUILDER in quests.completed)
        assertFalse(QuestIds.DIGITAL_FASTING in quests.completed)
        assertEquals(300L, currency.balance.xp)
        assertEquals(70L, currency.balance.gold)
    }

    @Test
    fun `master builder does not complete on a single building`() = runTest {
        val date = dateWithWindow(
            QuestIds.MASTER_BUILDER, QuestIds.DAWN_DISCIPLINE, QuestIds.DIGITAL_FASTING,
        )
        val (uc, _, quests) = useCase(
            clock = FixedClock(at(8, date)),
            screenTimeRepo = StubQuestScreenTimeRepo(instagramMs = 20 * 60_000L),
            inventoryDao = FakeInventoryDao(buildingsToday = 1),
        )

        uc()

        assertFalse(QuestIds.MASTER_BUILDER in quests.completed)
    }

    @Test
    fun `dawn discipline completes after 9am when the pre-dawn window was clean`() = runTest {
        val date = dateWithWindow(
            QuestIds.DAWN_DISCIPLINE, QuestIds.DIGITAL_FASTING, QuestIds.SANCTUARY_BUILDER,
        )
        val (uc, currency, quests) = useCase(
            clock = FixedClock(at(10, date)),
            screenTimeRepo = StubQuestScreenTimeRepo(
                instagramMs = 20 * 60_000L, // Digital Fasting fails
                windowFlaggedMs = 0L,
            ),
        )

        uc()

        assertTrue(QuestIds.DAWN_DISCIPLINE in quests.completed)
        assertEquals(150L, currency.balance.xp)
        assertEquals(30L, currency.balance.gold)
    }

    @Test
    fun `dawn discipline stays incomplete before 9am`() = runTest {
        val date = dateWithWindow(
            QuestIds.DAWN_DISCIPLINE, QuestIds.DIGITAL_FASTING, QuestIds.SANCTUARY_BUILDER,
        )
        val (uc, _, quests) = useCase(
            clock = FixedClock(at(7, date)),
            screenTimeRepo = StubQuestScreenTimeRepo(instagramMs = 20 * 60_000L, windowFlaggedMs = 0L),
        )

        uc()

        assertFalse(QuestIds.DAWN_DISCIPLINE in quests.completed)
    }

    @Test
    fun `dawn discipline fails after 9am when the window had usage`() = runTest {
        val date = dateWithWindow(
            QuestIds.DAWN_DISCIPLINE, QuestIds.DIGITAL_FASTING, QuestIds.SANCTUARY_BUILDER,
        )
        val (uc, _, quests) = useCase(
            clock = FixedClock(at(10, date)),
            screenTimeRepo = StubQuestScreenTimeRepo(instagramMs = 20 * 60_000L, windowFlaggedMs = 4 * 60_000L),
        )

        uc()

        assertFalse(QuestIds.DAWN_DISCIPLINE in quests.completed)
    }

    // ── invariants ──────────────────────────────────────────────────────────

    /**
     * Pro-perks constraint (docs/superpowers/specs/2026-08-28-pro-perks-design.md): the 2x Pro
     * multiplier applies only to detox saved-time rewards, never to the flat daily-quest rewards.
     * This use case has no premium input by design; this test makes that a loud failure if a
     * multiplier is ever threaded into the quest grant path.
     */
    @Test
    fun `quest rewards are granted at the flat catalog amount, never multiplier-scaled`() = runTest {
        val (uc, currency, quests) = useCase(
            clock = FixedClock(at(13)), // after noon so Deep Focus Shield resolves
            screenTimeRepo = StubQuestScreenTimeRepo(instagramMs = 0L, windowFlaggedMs = 0L),
            inventoryDao = FakeInventoryDao(buildingsToday = 1),
        )

        uc()

        val activeToday = questsForDay(FIXED_DATE)
        assertEquals(activeToday.size, quests.completed.size, "all of today's quests should complete")
        assertEquals(activeToday.sumOf { it.xpReward }, currency.balance.xp)
        assertEquals(activeToday.sumOf { it.goldReward }, currency.balance.gold)
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
