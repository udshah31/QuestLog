package com.questlog.domain.unlock

import com.questlog.domain.unlock.MindfulUnlockRule.GRACE_MS
import com.questlog.domain.unlock.MindfulUnlockRule.graceFor
import kotlin.test.Test
import kotlin.test.assertEquals

class MindfulUnlockRuleTest {
    @Test fun `purposeful task categories at the threshold earn grace`() {
        for (c in listOf("message", "create", "lookup", "work")) assertEquals(GRACE_MS, graceFor(0.7, c), c)
    }

    @Test fun `just under the threshold earns nothing`() = assertEquals(0L, graceFor(0.69, "message"))

    @Test fun `drifting categories earn nothing however purposeful`() {
        for (c in listOf("boredom", "habit", "unclear")) assertEquals(0L, graceFor(0.99, c), c)
    }

    @Test fun `grace is five minutes`() = assertEquals(5 * 60_000L, GRACE_MS)

    @Test fun `unknown or empty categories earn nothing`() {
        for (c in listOf("banana", "", "MESSAGE")) assertEquals(0L, graceFor(0.99, c), c)
    }
}
