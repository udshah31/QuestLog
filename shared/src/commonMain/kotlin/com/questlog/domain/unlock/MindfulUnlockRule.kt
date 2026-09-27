package com.questlog.domain.unlock

/** Decides the reward-free grace a judged Mindful Unlock earns. Pure. */
object MindfulUnlockRule {
    const val GRACE_MS = 5 * 60_000L
    /** The one tuning knob: Noul probability that the reason is a bounded task. */
    const val PURPOSEFUL_THRESHOLD = 0.7
    const val FREE_UNLOCKS_PER_DAY = 1

    private val DRIFTING = setOf("boredom", "habit", "unclear")

    fun graceFor(purposeful: Double, category: String): Long =
        if (purposeful >= PURPOSEFUL_THRESHOLD && category !in DRIFTING) GRACE_MS else 0L
}
