package com.example.questlog.ui.paywall

import com.example.questlog.ui.dashboard.PaywallReason
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaywallButtonTest {
    @Test fun `trial offer`() = assertEquals(
        PaywallButton("Start free trial", PaywallAction.Buy),
        paywallButton("$4.99 / month", "7 days free", offerLoading = false, demoAvailable = false),
    )

    @Test fun `offer without trial shows the price`() = assertEquals(
        PaywallButton("Unlock — $4.99 / month", PaywallAction.Buy),
        paywallButton("$4.99 / month", null, offerLoading = false, demoAvailable = false),
    )

    @Test fun `loading never claims unavailable`() = assertEquals(
        PaywallButton("Loading price…", PaywallAction.None),
        paywallButton(null, null, offerLoading = true, demoAvailable = false),
    )

    @Test fun `no offer in a debug build falls back to the demo`() = assertEquals(
        PaywallButton("Unlock — demo", PaywallAction.Demo),
        paywallButton(null, null, offerLoading = false, demoAvailable = true),
    )

    @Test fun `no offer in a release build is disabled`() = assertEquals(
        PaywallButton("Pro unavailable right now", PaywallAction.None),
        paywallButton(null, null, offerLoading = false, demoAvailable = false),
    )

    @Test fun `manual paywall with a trial discloses the price after the trial`() = assertEquals(
        "7 days free, then $4.99 / month.",
        paywallSubline(PaywallReason.Manual, "$4.99 / month", "7 days free"),
    )

    @Test fun `milestone sub-lines`() {
        assertEquals(
            "Your realm earned a trial: 7 days free, then $4.99 / month.",
            paywallSubline(PaywallReason.Milestone, "$4.99 / month", "7 days free"),
        )
        assertEquals("Keep the whole realm: $4.99 / month.", paywallSubline(PaywallReason.Milestone, "$4.99 / month", null))
    }

    @Test fun `manual without trial or any offer has no sub-line`() {
        assertNull(paywallSubline(PaywallReason.Manual, "$4.99 / month", null))
        assertNull(paywallSubline(PaywallReason.Milestone, null, null))
    }
}
