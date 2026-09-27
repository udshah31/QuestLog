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

    @Test fun `unknown entitlements count as Pro for gating, known free does not`() {
        val billing = BillingManager()
        assertTrue(billing.isProOrUnknown())
        billing.setDebugPremium(false)
        assertFalse(billing.isProOrUnknown())
        billing.setDebugPremium(true)
        assertTrue(billing.isProOrUnknown())
    }
}
