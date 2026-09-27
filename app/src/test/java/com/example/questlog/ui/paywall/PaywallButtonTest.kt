package com.example.questlog.ui.paywall

import org.junit.Test
import kotlin.test.assertEquals

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
}
