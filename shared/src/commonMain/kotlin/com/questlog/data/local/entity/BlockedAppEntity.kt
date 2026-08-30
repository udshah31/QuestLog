package com.questlog.data.local.entity

import androidx.room.Entity

/**
 * One row per app the user has marked as a distraction. Row existence *is* the
 * toggle — there is no `enabled` column. [dailyLimitMs] 0 = fully blocked.
 */
@Entity(tableName = "blocked_app", primaryKeys = ["packageName"])
data class BlockedAppEntity(
    val packageName: String,
    val dailyLimitMs: Long = 0L,
)
