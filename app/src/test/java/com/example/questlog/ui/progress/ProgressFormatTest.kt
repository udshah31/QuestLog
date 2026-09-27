package com.example.questlog.ui.progress

import com.questlog.domain.model.DaySaved
import com.questlog.util.TimeConversion
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressFormatTest {
    private fun week(vararg ms: Long) = ms.mapIndexed { i, v -> DaySaved(LocalDate(2026, 9, 21 + i), v, i == ms.lastIndex) }

    @Test fun `day initials`() = assertEquals(
        listOf("M", "T", "W", "T", "F", "S", "S"),
        DayOfWeek.entries.map { dayInitial(it) },
    )

    @Test fun `empty week until two days have saved time - zero days don't count`() {
        assertTrue(showsEmptyWeek(week(0, 0, 0, 0, 0, 0, 0)))
        assertTrue(showsEmptyWeek(week(0, 0, 0, 0, 0, 0, 5)))
        assertFalse(showsEmptyWeek(week(0, 0, 0, 0, 0, 3, 5)))
    }

    @Test fun `bars scale to the week's max, and an all-zero week never divides by zero`() {
        val w = week(0, 0, 0, 0, 0, 10, 40)
        assertEquals(1f, barFraction(40, w))
        assertEquals(0.25f, barFraction(10, w))
        assertEquals(0f, barFraction(0, week(0, 0, 0, 0, 0, 0, 0)))
    }

    @Test fun `xp to go stays positive exactly on a level boundary`() {
        val atLevel3 = TimeConversion.xpForLevel(3)
        assertEquals(TimeConversion.xpForLevel(4) - atLevel3, xpToGo(atLevel3, TimeConversion.xpForLevel(4)))
        assertTrue(xpToGo(atLevel3, TimeConversion.xpForLevel(4)) > 0)
    }

    @Test fun `reclaimed line`() {
        assertEquals("1h 30m", reclaimedLine(90 * 60_000L))
        assertEquals("45m", reclaimedLine(45 * 60_000L))
        assertEquals("0m", reclaimedLine(0))
    }
}
