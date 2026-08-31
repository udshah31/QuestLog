package com.example.questlog.theme

import androidx.compose.ui.graphics.Color
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Guards the "Palette #1 only" decision: charcoal ink on paper, one red accent, and no
 * separate dark theme. If someone reintroduces a dark palette they must revisit the design
 * (and this test) deliberately, not by accident.
 */
class PaletteTest {

    @Test
    fun `dark and light color sets are the same Palette 1 instance`() {
        assertSame(
            questLightColors, questDarkColors,
            "dark mode is not designed for Palette #1 — both sets must point at the same instance",
        )
    }

    @Test
    fun `core Palette 1 values are wired to the semantic tokens`() {
        assertEquals(Color(0xFFFAF7FF), questLightColors.ground, "ground = paper")
        assertEquals(Color(0xFF303841), questLightColors.inkPrimary, "inkPrimary = charcoal")
        assertEquals(Color(0xFF48545C), questLightColors.inkSecondary, "inkSecondary = slate")
        assertEquals(Color(0xFFD72323), questLightColors.earned, "earned = the one red accent")
        assertEquals(questLightColors.earned, questLightColors.locked, "Pro/locked reuses the red accent")
    }
}
