package com.example.questlog.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
)

interface InstalledAppsProvider {
    /** Launchable apps on the device, minus our own package, sorted by label. */
    suspend fun launchableApps(): List<InstalledApp>
}

class PackageManagerAppsProvider(private val context: Context) : InstalledAppsProvider {

    override suspend fun launchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, 0)
        resolved.asSequence()
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .map { pkg ->
                InstalledApp(
                    packageName = pkg,
                    label = runCatching {
                        pm.getApplicationInfo(pkg, 0).let { pm.getApplicationLabel(it).toString() }
                    }.getOrDefault(pkg),
                    icon = runCatching { pm.getApplicationIcon(pkg) }.getOrNull(),
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}
