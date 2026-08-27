package com.questlog.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks how long a specific distraction app was in the foreground on a given date,
 * and how many milliseconds the user "saved" by avoiding it relative to their goal.
 */
@Entity(tableName = "screen_time_records")
data class ScreenTimeRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    /** ISO-8601 local date string, e.g. "2026-08-27" */
    val date: String,
    val foregroundMs: Long,
    val savedMs: Long,
)
