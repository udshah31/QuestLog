package com.questlog.domain.usecase

import com.questlog.data.repository.CurrencyRepository
import com.questlog.data.repository.InventoryRepository
import com.questlog.domain.model.CityTile
import com.questlog.domain.model.PlayerStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class DashboardState(
    val stats: PlayerStats,
    val cityTiles: List<CityTile>,
)

class GetDashboardStatsUseCase(
    private val currencyRepo: CurrencyRepository,
    private val inventoryRepo: InventoryRepository,
) {
    operator fun invoke(todaySavedMs: Long = 0L): Flow<DashboardState> =
        combine(
            currencyRepo.observePlayerStats(todaySavedMs),
            inventoryRepo.observeBuildings(),
        ) { stats, tiles -> DashboardState(stats, tiles) }
}
