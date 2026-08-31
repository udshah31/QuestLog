package com.questlog.domain.platform

import com.questlog.domain.model.AppUsage

actual open class ScreenTimeTracker {
    actual open suspend fun getUsageForPeriod(startMs: Long, endMs: Long): List<AppUsage> {
        return emptyList()
    }

    actual open fun isPermissionGranted(): Boolean {
        return true
    }
}
