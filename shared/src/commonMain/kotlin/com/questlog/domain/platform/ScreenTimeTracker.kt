package com.questlog.domain.platform

import com.questlog.domain.model.AppUsage

/**
 * Platform-specific contract for querying app foreground usage.
 * Android: implemented via UsageStatsManager (PACKAGE_USAGE_STATS permission required).
 */
expect class ScreenTimeTracker {
    /** Returns usage data for the given epoch-ms time window. */
    suspend fun getUsageForPeriod(startMs: Long, endMs: Long): List<AppUsage>

    /** True if the app has been granted the PACKAGE_USAGE_STATS permission. */
    fun isPermissionGranted(): Boolean
}
