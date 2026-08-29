package com.example.questlog.theme

import androidx.compose.ui.graphics.Color
import org.junit.Test
import kotlin.math.pow
import kotlin.test.assertTrue

class ContrastTest {

    private fun channel(c: Float): Double {
        val s = c.toDouble()
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun ratio(a: Color, b: Color): Double {
        val la = luminance(a); val lb = luminance(b)
        val hi = maxOf(la, lb); val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun check(name: String, fg: Color, bg: Color) {
        val r = ratio(fg, bg)
        assertTrue(r >= 4.5, "$name: contrast ${"%.2f".format(r)} < 4.5")
    }

    @Test
    fun `small text tokens clear WCAG AA on ground and surface, both themes`() {
        for ((theme, c) in listOf("dark" to questDarkColors, "light" to questLightColors)) {
            for (bgName in listOf("ground", "surface")) {
                val bg = if (bgName == "ground") c.ground else c.surface
                check("$theme inkSecondary/$bgName", c.inkSecondary, bg)
                check("$theme inkMuted/$bgName", c.inkMuted, bg)
                check("$theme earned/$bgName", c.earned, bg)
                check("$theme currency/$bgName", c.currency, bg)
            }
        }
    }
}
