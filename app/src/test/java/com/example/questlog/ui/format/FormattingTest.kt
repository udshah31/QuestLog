package com.example.questlog.ui.format

import org.junit.Test
import kotlin.test.assertEquals

class FormattingTest {

    @Test
    fun `formatReclaimed splits hours and minutes`() {
        assertEquals(ReclaimedText(null, "0m"), formatReclaimed(0L))
        assertEquals(ReclaimedText(null, "45m"), formatReclaimed(45 * 60_000L))
        assertEquals(ReclaimedText("1h", "30m"), formatReclaimed(90 * 60_000L))
        assertEquals(ReclaimedText("2h", "5m"), formatReclaimed(125 * 60_000L))
        assertEquals(ReclaimedText("1h", "0m"), formatReclaimed(60 * 60_000L))
    }

    @Test
    fun `formatReclaimed floors partial minutes`() {
        assertEquals(ReclaimedText(null, "1m"), formatReclaimed(119_000L))
    }

    @Test
    fun `formatMultiplier is one decimal with the times glyph`() {
        assertEquals("2.0×", formatMultiplier(2.0f))
        assertEquals("1.0×", formatMultiplier(1.0f))
        assertEquals("3.0×", formatMultiplier(3.0f))
        assertEquals("1.5×", formatMultiplier(1.5f))
    }

    @Test
    fun `levelTitle maps 1 to 4 then falls through`() {
        assertEquals("Novice of Will", levelTitle(1))
        assertEquals("Seeker of Focus", levelTitle(2))
        assertEquals("Guardian of Time", levelTitle(3))
        assertEquals("Knight of Discipline", levelTitle(4))
        assertEquals("Grandmaster of Focus", levelTitle(5))
        assertEquals("Grandmaster of Focus", levelTitle(9))
    }

    @Test
    fun `ringFraction clamps to 0 and 1`() {
        assertEquals(0f, ringFraction(0L, 90 * 60_000L))
        assertEquals(0.5f, ringFraction(45 * 60_000L, 90 * 60_000L))
        assertEquals(1f, ringFraction(200 * 60_000L, 90 * 60_000L))
        assertEquals(0f, ringFraction(10L, 0L))
    }
}
