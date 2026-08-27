package com.questlog.domain.model

/** A building slot in the player's city grid. */
data class CityTile(
    val itemId: String,
    val displayName: String,
    val tier: Int,
    val isPremium: Boolean,
    val isOwned: Boolean,
    val goldCost: Long,
)
