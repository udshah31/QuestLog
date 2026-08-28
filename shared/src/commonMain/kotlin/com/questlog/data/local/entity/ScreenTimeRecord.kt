package com.questlog.data.local.entity

import androidx.room.Entity

/**
 * How long a specific distraction app was in the foreground on a given date.
 *
 * Keyed by (packageName, date): there is exactly one record per app per day, so
 * re-persisting today's usage replaces the existing row instead of appending a new one.
 * "Saved time" is computed for the day as a whole (see [com.questlog.util.DetoxBudget]),
 * not stored per app.
 */
@Entity(tableName = "screen_time_records", primaryKeys = ["packageName", "date"])
data class ScreenTimeRecord(
    val packageName: String,
    /** ISO-8601 local date string, e.g. "2026-08-27" */
    val date: String,
    val foregroundMs: Long,
)
