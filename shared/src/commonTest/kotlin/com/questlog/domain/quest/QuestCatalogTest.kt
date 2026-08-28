package com.questlog.domain.quest

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuestCatalogTest {

    @Test
    fun `the catalog holds eight quests with unique ids`() {
        assertEquals(8, questCatalog.size)
        assertEquals(8, questCatalog.map { it.id }.toSet().size)
    }

    @Test
    fun `questsForDay returns exactly three quests`() {
        assertEquals(3, questsForDay(LocalDate(2026, 8, 28)).size)
    }

    @Test
    fun `questsForDay is deterministic for a given date`() {
        val date = LocalDate(2026, 8, 28)
        assertEquals(questsForDay(date), questsForDay(date))
    }

    @Test
    fun `the window starts at epochDays mod 8 and wraps the catalog`() {
        // 1970-01-01 is epoch day 0 -> k = 0
        assertEquals(
            questCatalog.subList(0, 3).map { it.id },
            questsForDay(LocalDate(1970, 1, 1)).map { it.id },
        )
        // 1970-01-07 is epoch day 6 -> k = 6, window = [6, 7, 0]
        assertEquals(
            listOf(questCatalog[6].id, questCatalog[7].id, questCatalog[0].id),
            questsForDay(LocalDate(1970, 1, 7)).map { it.id },
        )
    }

    @Test
    fun `the rotation has period eight`() {
        val day0 = questsForDay(LocalDate(1970, 1, 1))
        val day8 = questsForDay(LocalDate(1970, 1, 9)) // epoch day 8
        assertEquals(day0, day8)
    }

    @Test
    fun `consecutive days share exactly two quests`() {
        var date = LocalDate(2026, 1, 1)
        repeat(20) {
            val today = questsForDay(date).map { it.id }.toSet()
            val next = questsForDay(date.plusDays()).map { it.id }.toSet()
            assertEquals(2, today.intersect(next).size, "days $date and its successor")
            date = date.plusDays()
        }
    }

    @Test
    fun `every quest is active exactly three days in any eight-day window`() {
        val start = LocalDate(2026, 3, 10)
        val counts = mutableMapOf<String, Int>()
        var date = start
        repeat(8) {
            questsForDay(date).forEach { counts.merge(it.id, 1, Int::plus) }
            date = date.plusDays()
        }
        assertEquals(questCatalog.map { it.id }.toSet(), counts.keys)
        assertTrue(counts.values.all { it == 3 }, "each quest appears 3 times: $counts")
    }

    @Test
    fun `no day's trio is entirely one reward tier`() {
        // A soft variety check: within a day, the three quests are never all identical XP.
        var date = LocalDate(2026, 6, 1)
        repeat(8) {
            val xp = questsForDay(date).map { it.xpReward }.toSet()
            assertTrue(xp.size >= 2, "day $date quests all share one XP value")
            date = date.plusDays()
        }
    }
}

private fun LocalDate.plusDays(): LocalDate = LocalDate.fromEpochDays(toEpochDays() + 1)
