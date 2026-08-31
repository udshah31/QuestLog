package com.questlog.data.repository

import com.questlog.data.local.dao.CurrencyDao
import com.questlog.data.local.entity.CurrencyBalance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.toLocalDateTime

/**
 * Faithfully models Room's behaviour for a *fresh install*: the single balance row
 * does not exist yet, and an `UPDATE ... WHERE id = 1` therefore matches nothing.
 */
private class FreshInstallCurrencyDao : CurrencyDao {
    val state = MutableStateFlow<CurrencyBalance?>(null)

    override suspend fun upsert(balance: CurrencyBalance) { state.value = balance }

    override suspend fun insertIfAbsent(balance: CurrencyBalance) {
        if (state.value == null) state.value = balance
    }

    override fun observe(): Flow<CurrencyBalance?> = state
    override suspend fun get(): CurrencyBalance? = state.value

    override suspend fun addRewards(xpDelta: Long, goldDelta: Long, now: Long) {
        val current = state.value ?: return // UPDATE WHERE id = 1 matches no rows
        state.value = current.copy(
            xp = current.xp + xpDelta,
            gold = current.gold + goldDelta,
            updatedAt = now,
        )
    }

    override suspend fun updateDailyAward(date: String, awardedSavedMs: Long, now: Long) {
        val current = state.value ?: return
        state.value = current.copy(rewardDate = date, awardedSavedMsToday = awardedSavedMs, updatedAt = now)
    }

    override suspend fun setStreak(days: Int, now: Long) {
        val current = state.value ?: return
        state.value = current.copy(consecutiveDetoxDays = days, updatedAt = now)
    }

    override suspend fun addLifetimeSaved(deltaMs: Long, now: Long) {
        val current = state.value ?: return
        state.value = current.copy(lifetimeSavedMs = current.lifetimeSavedMs + deltaMs, updatedAt = now)
    }

    override suspend fun setStreakFreezeUsed(date: String, now: Long) {
        val current = state.value ?: return
        state.value = current.copy(streakFreezeLastUsed = date, updatedAt = now)
    }
}

class CurrencyRepositoryTest {

    @Test
    fun `addRewards on a fresh install is persisted, not silently dropped`() = runTest {
        val dao = FreshInstallCurrencyDao()
        val repo = CurrencyRepository(dao)

        repo.addRewards(xpDelta = 120L, goldDelta = 40L)

        val stats = repo.observePlayerStats().first()
        assertEquals(120L, stats.xp)
        assertEquals(40L, stats.gold)
    }

    @Test
    fun `setDailyAward on a fresh install creates the balance row`() = runTest {
        val dao = FreshInstallCurrencyDao()
        val repo = CurrencyRepository(dao)

        repo.setDailyAward(date = "2026-08-27", awardedSavedMs = 1_800_000L)

        val balance = repo.currentBalance()
        assertEquals("2026-08-27", balance?.rewardDate)
        assertEquals(1_800_000L, balance?.awardedSavedMsToday)
    }

    @Test
    fun `ensureInitialized never overwrites an already-funded balance`() = runTest {
        val dao = FreshInstallCurrencyDao()
        val repo = CurrencyRepository(dao)

        repo.addRewards(xpDelta = 500L, goldDelta = 300L)
        repo.ensureInitialized()
        repo.ensureInitialized()

        val stats = repo.observePlayerStats().first()
        assertEquals(500L, stats.xp)
        assertEquals(300L, stats.gold)
    }

    @Test
    fun `streakFreezeReady is true until a charge is spent`() = runTest {
        val dao = FreshInstallCurrencyDao()
        val repo = CurrencyRepository(dao)
        repo.addRewards(1L, 1L) // seeds the row; streakFreezeLastUsed defaults to ""

        assertTrue(repo.observePlayerStats().first().streakFreezeReady)

        val today = kotlinx.datetime.Clock.System.now()
            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date.toString()
        repo.setStreakFreezeUsed(today)

        assertFalse(repo.observePlayerStats().first().streakFreezeReady)
    }

    @Test
    fun `lifetimeSavedMs is the stored total plus today's not-yet-finalised award`() = runTest {
        val dao = FreshInstallCurrencyDao()
        dao.state.value = CurrencyBalance(
            id = 1L,
            lifetimeSavedMs = 3 * 60 * 60_000L,      // 3h locked in from earlier days
            awardedSavedMsToday = 20 * 60_000L,      // 20m so far today
        )
        val repo = CurrencyRepository(dao)

        assertEquals(3 * 60 * 60_000L + 20 * 60_000L, repo.observePlayerStats().first().lifetimeSavedMs)
    }

    @Test
    fun `addLifetimeSaved accumulates onto the stored total`() = runTest {
        val dao = FreshInstallCurrencyDao()
        val repo = CurrencyRepository(dao)
        repo.addRewards(1L, 0L) // seed the row

        repo.addLifetimeSaved(45 * 60_000L)
        repo.addLifetimeSaved(15 * 60_000L)

        assertEquals(60 * 60_000L, dao.state.value!!.lifetimeSavedMs)
    }
}
