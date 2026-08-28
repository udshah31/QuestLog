package com.questlog.data.local.entity

import androidx.room.Entity

/**
 * Tracks how long a specific distraction app was in the foreground on a given date,
 * and how many milliseconds the user "saved" by avoiding it relative to their goal.
 *
 * Keyed by (packageName, date): there is exactly one record per app per day, so
 * re-persisting today's usage replaces the existing row instead of appending a new one.
 */
@Entity(tableName = "screen_time_records", primaryKeys = ["packageName", "date"])
data class ScreenTimeRecord(
    val packageName: String,
    /** ISO-8601 local date string, e.g. "2026-08-27" */
    val date: String,
    val foregroundMs: Long,
    val savedMs: Long,
)
