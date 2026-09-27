package com.example.questlog.billing

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BillingManagerTest {
    @Test fun `loadProOffer returns null when Purchases is not configured`() = runTest {
        assertNull(BillingManager().loadProOffer())
    }

    @Test fun `entitlements are unknown until customer info or a debug override arrives`() {
        val billing = BillingManager()
        assertFalse(billing.entitlementsKnown.value)
        billing.setDebugPremium(false)
        assertTrue(billing.entitlementsKnown.value)
    }
}
