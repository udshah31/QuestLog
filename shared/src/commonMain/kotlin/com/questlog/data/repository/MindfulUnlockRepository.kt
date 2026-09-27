package com.questlog.data.repository

import com.questlog.data.local.dao.MindfulUnlockDao
import com.questlog.data.local.entity.MindfulUnlockEntity
import com.questlog.domain.unlock.MindfulUnlockRule
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class MindfulUnlockRepository(
    private val dao: MindfulUnlockDao,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    private fun today(): String = clock.now().toLocalDateTime(timeZone).date.toString()

    /** Records a judged unlock and returns the grace it earned (0 if not purposeful). */
    suspend fun record(packageName: String, category: String, purposeful: Double): Long {
        val grace = MindfulUnlockRule.graceFor(purposeful, category)
        dao.insert(
            MindfulUnlockEntity(
                date = today(),
                packageName = packageName,
                category = category,
                purposeful = purposeful,
                graceMs = grace,
                createdAt = clock.now().toEpochMilliseconds(),
            ),
        )
        return grace
    }

    suspend fun graceMsToday(): Map<String, Long> =
        dao.graceByPackageForDate(today()).associate { it.packageName to it.graceMs }

    suspend fun countToday(): Int = dao.countForDate(today())
}
