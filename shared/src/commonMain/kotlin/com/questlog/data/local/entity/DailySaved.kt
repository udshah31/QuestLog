package com.questlog.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One finished day's reclaimed time, written on day rollover. For the Progress chart. */
@Entity(tableName = "daily_saved")
data class DailySaved(
    @PrimaryKey val date: String, // ISO-8601 local date, e.g. "2026-08-31"
    val savedMs: Long,
)
