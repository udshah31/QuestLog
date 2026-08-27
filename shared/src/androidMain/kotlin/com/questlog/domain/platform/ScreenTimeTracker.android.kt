package com.questlog.domain.platform

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.questlog.domain.model.AppUsage

actual class ScreenTimeTracker(private val context: Context? = null) {

    private val usageStatsManager: UsageStatsManager? =
        context?.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    /**
     * Uses the event-based API for accurate foreground-time calculation.
     * Builds a per-package state machine: MOVE_TO_FOREGROUND starts a timer,
     * MOVE_TO_BACKGROUND / SCREEN_NON_INTERACTIVE stops it.
     */
    actual suspend fun getUsageForPeriod(startMs: Long, endMs: Long): List<AppUsage> {
        val manager = usageStatsManager ?: return emptyList()
        val events = manager.queryEvents(startMs, endMs) ?: return emptyList()
        val foregroundStart = mutableMapOf<String, Long>()
        val accumulated = mutableMapOf<String, Long>()

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    foregroundStart[event.packageName] = event.timeStamp
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val start = foregroundStart.remove(event.packageName) ?: continue
                    accumulated[event.packageName] =
                        (accumulated[event.packageName] ?: 0L) + (event.timeStamp - start)
                }
            }
        }

        // Close any still-foreground sessions at endMs
        for ((pkg, start) in foregroundStart) {
            accumulated[pkg] = (accumulated[pkg] ?: 0L) + (endMs - start)
        }

        return accumulated.map { (pkg, ms) -> AppUsage(pkg, ms) }
    }

    actual fun isPermissionGranted(): Boolean {
        val ctx = context ?: return false
        val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            ctx.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
