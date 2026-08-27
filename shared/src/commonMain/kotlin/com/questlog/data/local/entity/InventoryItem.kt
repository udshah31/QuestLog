package com.questlog.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ItemType { BUILDING, COSMETIC }

/**
 * Represents a city building or cosmetic item owned by the player.
 * Premium items require an active RevenueCat entitlement to unlock.
 */
@Entity(tableName = "inventory_items")
data class InventoryItem(
    @PrimaryKey val itemId: String,
    val type: ItemType,
    val tier: Int = 1,
    val isPremium: Boolean = false,
    val acquiredAt: Long = 0L,
)
