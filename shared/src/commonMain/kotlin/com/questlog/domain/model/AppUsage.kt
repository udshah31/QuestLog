package com.questlog.domain.model

/** Raw usage data for a single package within a queried time window. */
data class AppUsage(
    val packageName: String,
    val totalForegroundMs: Long,
)
