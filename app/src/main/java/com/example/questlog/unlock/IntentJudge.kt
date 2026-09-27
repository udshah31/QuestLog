package com.example.questlog.unlock

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

sealed interface Verdict {
    data class Judged(val purposeful: Double, val category: String) : Verdict
    /** Offline, timeout, non-200, bad JSON, or no proxy configured. Never grants grace. */
    data object Unavailable : Verdict
}

/** Parses the proxy's `{"purposeful": 0..1, "category": "..."}`; anything else is Unavailable. */
fun parseVerdict(json: String): Verdict = runCatching {
    val o = Json.parseToJsonElement(json).jsonObject
    val p = o.getValue("purposeful").jsonPrimitive
    val c = o.getValue("category").jsonPrimitive
    require(!p.isString && c.isString)
    val purposeful = p.double
    require(purposeful in 0.0..1.0)
    Verdict.Judged(purposeful, c.content)
}.getOrDefault(Verdict.Unavailable)

/** The proxy rejects labels over 60 chars; truncate rather than fail forever for that app. */
fun judgeRequestBody(appLabel: String, reason: String): String =
    buildJsonObject { put("app", appLabel.take(60)); put("reason", reason) }.toString()

/** Calls the Mindful Unlocks proxy. `open` so ViewModel tests can script verdicts. */
open class IntentJudge(
    private val baseUrl: String,
    private val installId: () -> String,
) {
    open suspend fun judge(appLabel: String, reason: String): Verdict {
        if (!baseUrl.startsWith("https://")) return Verdict.Unavailable
        return withContext(Dispatchers.IO) {
            try {
                val conn = URL("${baseUrl.trimEnd('/')}/judge").openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "POST"
                    conn.connectTimeout = TIMEOUT_MS
                    conn.readTimeout = TIMEOUT_MS
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("X-Install-Id", installId())
                    val body = judgeRequestBody(appLabel, reason)
                    conn.outputStream.use { it.write(body.toByteArray()) }
                    if (conn.responseCode != 200) Verdict.Unavailable
                    else parseVerdict(conn.inputStream.bufferedReader().use { it.readText() })
                } finally {
                    conn.disconnect()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Verdict.Unavailable
            }
        }
    }

    private companion object { const val TIMEOUT_MS = 3_000 }
}

/** A random per-install id for the proxy's rate limit. Not tied to the user. */
object InstallId {
    private const val KEY = "install_id"
    fun get(prefs: SharedPreferences): String =
        prefs.getString(KEY, null) ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY, it).apply() }
}

/** Launches [packageName]; false if it has no launcher entry or is disabled/restricted. */
fun launchApp(context: Context, packageName: String): Boolean = runCatching {
    val launch = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
    context.startActivity(launch)
    true
}.getOrDefault(false)
