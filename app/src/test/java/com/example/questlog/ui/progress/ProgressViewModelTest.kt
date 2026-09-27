package com.example.questlog.ui.progress

import com.example.questlog.ui.dashboard.FakeCurrencyDao
import com.example.questlog.ui.dashboard.FakeQuestDao
import com.questlog.data.local.dao.DailySavedDao
import com.questlog.data.local.entity.DailySaved
import com.questlog.data.repository.BlocklistRepository
import com.questlog.data.repository.CurrencyRepository
import com.questlog.data.repository.DailySavedRepository
import com.questlog.domain.usecase.GetProgressStatsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class NoHistory : DailySavedDao {
    override suspend fun upsert(row: DailySaved) = Unit
    override fun observeSince(fromDate: String): Flow<List<DailySaved>> = MutableStateFlow(emptyList())
    override fun observeBestMsBefore(date: String): Flow<Long?> = MutableStateFlow(null)
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads the use case's stats`() = runTest {
        val vm = ProgressViewModel(
            GetProgressStatsUseCase(
                currencyRepo = CurrencyRepository(FakeCurrencyDao()),
                dailySavedRepo = DailySavedRepository(NoHistory()),
                questDao = FakeQuestDao(),
                blocklistRepo = BlocklistRepository(com.example.questlog.ui.dashboard.FakeBlocklistDao()),
                // a real midnight ticker would keep advanceUntilIdle() looping on its delay()
                dates = kotlinx.coroutines.flow.flowOf(kotlinx.datetime.LocalDate(2026, 9, 27)),
            ),
        )
        assertTrue(vm.uiState.value.isLoading)
        backgroundScope.launch { vm.uiState.collect {} } // WhileSubscribed needs a subscriber
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals(false, s.isLoading)
        assertEquals(7, s.stats?.last7Days?.size)
        assertEquals(3, s.stats?.streakDays) // DashboardViewModelTest's FakeCurrencyDao starts at a 3-day streak
    }
}
