package com.questlog.data.repository

import com.questlog.data.local.dao.InventoryDao
import com.questlog.data.local.entity.InventoryItem
import com.questlog.data.local.entity.ItemType
import com.questlog.domain.model.CityTile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

class InventoryRepository(private val dao: InventoryDao) {

    /** All available building definitions (in a production app, loaded from a local catalog). */
    private val buildingCatalog = listOf(
        CityTile("town_hall", "Town Hall", 1, false, false, 0L),
        CityTile("market", "Market", 1, false, false, 50L),
        CityTile("library", "Library", 2, false, false, 120L),
        CityTile("garden", "Zen Garden", 2, false, false, 200L),
        CityTile("castle", "Crystal Castle", 3, true, false, 0L),
        CityTile("fountain", "Aurora Fountain", 3, true, false, 0L),
    )

    fun observeBuildings(): Flow<List<CityTile>> =
        dao.getByType(ItemType.BUILDING).map { owned ->
            val ownedIds = owned.map { it.itemId }.toSet()
            buildingCatalog.map { it.copy(isOwned = it.itemId in ownedIds) }
        }

    suspend fun purchaseBuilding(tile: CityTile) {
        if (tile.isOwned) return
        dao.addItem(
            InventoryItem(
                itemId = tile.itemId,
                type = ItemType.BUILDING,
                tier = tile.tier,
                isPremium = tile.isPremium,
                acquiredAt = Clock.System.now().toEpochMilliseconds(),
            )
        )
    }

    suspend fun isOwned(itemId: String): Boolean = dao.isOwned(itemId)
}
