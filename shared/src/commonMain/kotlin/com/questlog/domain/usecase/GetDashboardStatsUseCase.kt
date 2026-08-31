package com.questlog.domain.usecase

import com.questlog.data.repository.BlocklistRepository
import com.questlog.data.repository.CurrencyRepository
import com.questlog.data.repository.InventoryRepository
import com.questlog.domain.model.CityTile
import com.questlog.domain.model.PlayerStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class DashboardState(
    val stats: PlayerStats,
    val cityTiles: List<CityTile>,
    val blockedAppCount: Int = 0,
)

class GetDashboardStatsUseCase(
    private val currencyRepo: CurrencyRepository,
    private val inventoryRepo: InventoryRepository,
    private val blocklistRepo: BlocklistRepository,
) {
    operator fun invoke(): Flow<DashboardState> =
        combine(
            currencyRepo.observePlayerStats(),
            inventoryRepo.observeBuildings(),
            blocklistRepo.observeBlockedApps(),
        ) { stats, tiles, blocked ->
            DashboardState(stats, tiles, blockedAppCount = blocked.size)
        }
}
