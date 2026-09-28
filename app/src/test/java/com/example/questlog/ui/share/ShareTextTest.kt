package com.example.questlog.ui.share

import com.questlog.domain.model.CityTile
import org.junit.Test
import kotlin.test.assertEquals

class ShareTextTest {
    private fun tile(owned: Boolean) = CityTile("id", "name", 1, false, owned, 0L)

    @Test fun `caption carries the all-time reclaimed time`() {
        assertEquals("I've reclaimed 4h 30m from my phone with QuestLog 🏰", shareCaption(270 * 60_000L))
        assertEquals("I've reclaimed 45m from my phone with QuestLog 🏰", shareCaption(45 * 60_000L))
    }

    @Test fun `built line counts owned tiles`() {
        assertEquals("6/6 built", builtLine(List(6) { tile(true) }))
        assertEquals("2/6 built", builtLine(listOf(true, true, false, false, false, false).map { tile(it) }))
        assertEquals("0/0 built", builtLine(emptyList()))
    }
}
