package com.questlog.domain.platform

import com.questlog.domain.model.AppUsage

actual class ScreenTimeTracker {
    actual suspend fun getUsageForPeriod(startMs: Long, endMs: Long): List<AppUsage> {
        return emptyList()
    }

    actual fun isPermissionGranted(): Boolean {
        return true
    }
}
