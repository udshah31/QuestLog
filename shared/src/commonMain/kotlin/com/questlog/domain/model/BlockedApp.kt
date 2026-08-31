package com.questlog.domain.model

/**
 * An app the user has marked as a distraction. [dailyLimitMs] is a daily
 * allowance — only foreground time beyond it counts against the detox reward.
 * A limit of 0 means the app is fully blocked (every millisecond counts).
 */
data class BlockedApp(
    val packageName: String,
    val dailyLimitMs: Long,
)
