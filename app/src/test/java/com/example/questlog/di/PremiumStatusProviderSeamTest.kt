package com.example.questlog.di

import com.example.questlog.appModule
import com.example.questlog.billing.BillingManager
import com.questlog.domain.PremiumStatusProvider
import org.junit.After
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Guards the one link that makes the 2x multiplier and the Streak Freeze Shield real in the
 * running app: `appModule` must bind a [PremiumStatusProvider], and `SharedModule` resolves it
 * with `getOrNull<PremiumStatusProvider>()`. That call swallows a missing/mistyped binding
 * silently — every premium user would quietly drop to 1x with no compile error and no test
 * failure — so this asserts the seam directly.
 */
class PremiumStatusProviderSeamTest {

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `appModule binds PremiumStatusProvider and it tracks BillingManager live`() {
        val koin = startKoin { modules(appModule) }.koin

        // Same resolution SharedModule performs: getOrNull, so a missing binding is a null, not a throw.
        val provider = koin.getOrNull<PremiumStatusProvider>()
        assertNotNull(provider, "appModule must bind PremiumStatusProvider or every premium perk silently no-ops")

        assertEquals(false, provider.isPremium(), "a fresh BillingManager is not premium")

        // Flipping billing must be visible through the provider on the next read — it delegates,
        // it does not snapshot at construction.
        koin.get<BillingManager>().setDebugPremium(true)
        assertEquals(true, provider.isPremium(), "provider must read BillingManager.isPremium live")
    }

    @Test
    fun `getOrNull returns null when no provider is bound so SharedModule falls back to non-premium`() {
        val koin = koinApplication { modules(module { single { BillingManager() } }) }.koin

        assertNull(
            koin.getOrNull<PremiumStatusProvider>(),
            "with no binding the seam must yield null (SharedModule maps that to isPremium=false)",
        )
    }
}
