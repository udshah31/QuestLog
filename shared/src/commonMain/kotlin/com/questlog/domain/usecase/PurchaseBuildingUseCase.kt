package com.questlog.domain.usecase

import com.questlog.data.repository.CurrencyRepository
import com.questlog.data.repository.InventoryRepository
import com.questlog.domain.model.CityTile
import kotlinx.coroutines.flow.first

sealed class PurchaseResult {
    object Success : PurchaseResult()
    object AlreadyOwned : PurchaseResult()
    object InsufficientFunds : PurchaseResult()
    object PremiumRequired : PurchaseResult()
}

class PurchaseBuildingUseCase(
    private val currencyRepo: CurrencyRepository,
    private val inventoryRepo: InventoryRepository,
) {
    suspend operator fun invoke(tile: CityTile, isPremiumUser: Boolean): PurchaseResult {
        if (inventoryRepo.isOwned(tile.itemId)) return PurchaseResult.AlreadyOwned
        if (tile.isPremium && !isPremiumUser) return PurchaseResult.PremiumRequired
        val balance = currencyRepo.observePlayerStats()
        val stats = balance.first()
        if (stats.gold < tile.goldCost) return PurchaseResult.InsufficientFunds
        // Deduct gold (negative reward)
        currencyRepo.addRewards(xpDelta = 0L, goldDelta = -tile.goldCost)
        inventoryRepo.purchaseBuilding(tile)
        return PurchaseResult.Success
    }
}
