package com.example.questlog.ui.share

import com.questlog.domain.model.CityTile
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShareTextTest {
    private fun tile(owned: Boolean, tier: Int = 1) = CityTile("id", "name", tier, false, owned, 0L)

    @Test fun `caption carries the all-time reclaimed time`() {
        assertEquals("I've reclaimed 4h 30m from my phone with QuestLog 🏰", shareCaption(270 * 60_000L))
        assertEquals("I've reclaimed 45m from my phone with QuestLog 🏰", shareCaption(45 * 60_000L))
    }

    @Test fun `built line counts owned tiles`() {
        assertEquals("6 of 6 built", builtLine(List(6) { tile(true) }))
        assertEquals("2 of 6 built", builtLine(listOf(true, true, false, false, false, false).map { tile(it) }))
        assertEquals("0 of 0 built", builtLine(emptyList()))
    }

    @Test fun `skyline is uneven, not a staircase`() {
        val heights = (0 until 6).map { towerHeight(it) }
        assertTrue(heights.zipWithNext().all { (a, b) -> a != b }, "neighbours differ: $heights")
        assertTrue(heights != heights.sorted() && heights != heights.sortedDescending(), "not monotonic: $heights")
        assertTrue(heights.all { it in 0.2f..1f }, "fractions stay drawable: $heights")
        assertEquals(towerHeight(0), towerHeight(6), "profile repeats for a bigger realm")
    }
}
