package com.questlog.domain.platform

import com.questlog.domain.model.AppUsage

/**
 * Platform-specific contract for querying app foreground usage.
 * Android: implemented via UsageStatsManager (PACKAGE_USAGE_STATS permission required).
 *
 * `open` on every target so tests can subclass it as a stub (the desktop `actual`
 * is a no-op); production never subclasses it.
 */
expect open class ScreenTimeTracker {
    /** Returns usage data for the given epoch-ms time window. */
    open suspend fun getUsageForPeriod(startMs: Long, endMs: Long): List<AppUsage>

    /** True if the app has been granted the PACKAGE_USAGE_STATS permission. */
    open fun isPermissionGranted(): Boolean
}
