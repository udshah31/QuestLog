package com.questlog.domain.usecase

import com.questlog.domain.model.DetoxMetrics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Emits [DetoxMetrics] on startup, then every [intervalMs] milliseconds.
 * Exposed to ViewModels as a StateFlow via stateIn().
 */
class DetoxMonitorFlow(
    private val calculateDetoxRewards: CalculateDetoxRewardsUseCase,
    private val intervalMs: Long = 60_000L,
) {
    operator fun invoke(): Flow<DetoxMetrics> = flow {
        while (true) {
            emit(calculateDetoxRewards())
            delay(intervalMs)
        }
    }
}
