package com.questlog.domain.usecase

import com.questlog.domain.model.DetoxMetrics
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private fun metrics(xp: Long): DetoxMetrics = DetoxMetrics(
    timeSavedMs = 0L,
    xpEarned = xp,
    goldEarned = 0L,
    currentLevel = 1,
    xpProgress = 0f,
    consecutiveDetoxDays = 0,
    streakMultiplier = 1f,
)

class DetoxMonitorFlowTest {

    @Test
    fun `emits on start and again after each interval`() = runTest {
        var ticks = 0
        val monitor = DetoxMonitorFlow(
            runDetoxCheck = { metrics(xp = (++ticks).toLong()) },
            intervalMs = 1_000L,
        )

        val emitted = monitor().take(3).toList()

        assertEquals(listOf(1L, 2L, 3L), emitted.map { it.xpEarned })
    }

    @Test
    fun `a failing tick is skipped and polling continues`() = runTest {
        var call = 0
        val monitor = DetoxMonitorFlow(
            runDetoxCheck = {
                call++
                if (call == 2) throw IllegalStateException("transient failure")
                metrics(xp = call.toLong())
            },
            intervalMs = 1_000L,
        )

        val emitted = monitor().take(3).toList()

        // Call 2 threw, so the successful emissions come from calls 1, 3 and 4.
        assertEquals(listOf(1L, 3L, 4L), emitted.map { it.xpEarned })
    }
}
