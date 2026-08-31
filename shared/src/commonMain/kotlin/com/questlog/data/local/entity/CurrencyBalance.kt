package com.questlog.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row table holding the player's live XP, gold, and gem totals.
 * Always use id = 1L for upsert operations.
 */
@Entity(tableName = "currency_balance")
data class CurrencyBalance(
    @PrimaryKey val id: Long = 1L,
    val xp: Long = 0L,
    val gold: Long = 0L,
    val gems: Long = 0L,
    val consecutiveDetoxDays: Int = 0,
    /** Local date ("yyyy-MM-dd") that [awardedSavedMsToday] applies to; "" before the first award. */
    val rewardDate: String = "",
    /** High-water mark of screen-time-saved (ms) already converted to rewards on [rewardDate]. */
    val awardedSavedMsToday: Long = 0L,
    /**
     * Saved screen-time (ms) locked in from days before [rewardDate]. The live all-time
     * total is `lifetimeSavedMs + awardedSavedMsToday`; on a day rollover the finalised
     * previous day's [awardedSavedMsToday] is folded in here.
     */
    val lifetimeSavedMs: Long = 0L,
    /** ISO date the streak-freeze charge was last spent; "" if never used / recharged. */
    val streakFreezeLastUsed: String = "",
    val updatedAt: Long = 0L,
)
