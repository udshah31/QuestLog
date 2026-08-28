package com.questlog.domain.usecase

import com.questlog.domain.model.DetoxMetrics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.coroutines.cancellation.CancellationException

/**
 * Emits [DetoxMetrics] on collection, then every [intervalMs] milliseconds.
 * A failing tick (permission not granted yet, transient DB error) is skipped
 * so the loop keeps polling instead of terminating on the first error.
 */
open class DetoxMonitorFlow(
    private val runDetoxCheck: suspend () -> DetoxMetrics,
    private val intervalMs: Long = 60_000L,
) {
    open operator fun invoke(): Flow<DetoxMetrics> = flow {
        while (true) {
            try {
                emit(runDetoxCheck())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Skip this tick; retry after the interval.
            }
            delay(intervalMs)
        }
    }
}
