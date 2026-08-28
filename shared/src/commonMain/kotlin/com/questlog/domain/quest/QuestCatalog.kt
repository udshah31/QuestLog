package com.questlog.domain.quest

import com.questlog.domain.model.DailyQuest
import kotlinx.datetime.LocalDate

/** Stable ids for the daily quests. */
object QuestIds {
    const val DIGITAL_FASTING = "digital_fasting"
    const val DEEP_FOCUS_SHIELD = "deep_focus_shield"
    const val SANCTUARY_BUILDER = "sanctuary_builder"
    const val FEED_FREEZE = "feed_freeze"
    const val CENTURY_SAVER = "century_saver"
    const val BUDGET_GUARDIAN = "budget_guardian"
    const val MASTER_BUILDER = "master_builder"
    const val DAWN_DISCIPLINE = "dawn_discipline"
}

/** Package whose usage the Digital Fasting quest limits. */
const val DIGITAL_FASTING_PACKAGE = "com.instagram.android"

/** Max Instagram foreground time that still completes Digital Fasting. */
const val DIGITAL_FASTING_MAX_MS = 15 * 60_000L

/** Deep Focus Shield window, local wall-clock hours. */
const val DEEP_FOCUS_START_HOUR = 9
const val DEEP_FOCUS_END_HOUR = 12

/** Feed Freeze: combined foreground across these must stay under [FEED_FREEZE_MAX_MS]. */
val FEED_FREEZE_PACKAGES: Set<String> = setOf(
    "com.instagram.android",       // Instagram
    "com.zhiliaoapp.musically",    // TikTok
    "com.twitter.android",         // X
)
const val FEED_FREEZE_MAX_MS = 10 * 60_000L

/** Century Saver completes once this much saved time is banked for the day. */
const val CENTURY_SAVER_MIN_SAVED_MS = 60 * 60_000L

/** Budget Guardian: total flagged-app foreground for the day must stay at or under this. */
const val BUDGET_GUARDIAN_MAX_FLAGGED_MS = 30 * 60_000L

/** Master Builder completes once this many buildings are constructed in a day. */
const val MASTER_BUILDER_MIN_BUILDINGS = 2

/** Dawn Discipline window end, local wall-clock hour (start is the start of the day). */
const val DAWN_DISCIPLINE_END_HOUR = 9

/**
 * The full quest pool. Only [QUESTS_PER_DAY] are active on a given day — see [questsForDay].
 * `isCompleted` here is a placeholder that [com.questlog.data.repository.DailyQuestRepository]
 * fills in per day. Order matters: the daily rotation is a sliding window over this list, so
 * it is arranged to keep each day's trio varied.
 */
val questCatalog: List<DailyQuest> = listOf(
    DailyQuest(
        id = QuestIds.DIGITAL_FASTING,
        title = "Digital Fasting",
        description = "Keep Instagram under 15 minutes today",
        xpReward = 150L,
        goldReward = 30L,
        isCompleted = false,
        icon = "🧘",
    ),
    DailyQuest(
        id = QuestIds.SANCTUARY_BUILDER,
        title = "Sanctuary Builder",
        description = "Construct any building in your realm today",
        xpReward = 200L,
        goldReward = 50L,
        isCompleted = false,
        icon = "🔨",
    ),
    DailyQuest(
        id = QuestIds.DEEP_FOCUS_SHIELD,
        title = "Deep Focus Shield",
        description = "No distraction apps between 9am and 12pm",
        xpReward = 300L,
        goldReward = 80L,
        isCompleted = false,
        icon = "🛡️",
    ),
    DailyQuest(
        id = QuestIds.FEED_FREEZE,
        title = "Feed Freeze",
        description = "Instagram, TikTok and X under 10 minutes combined",
        xpReward = 200L,
        goldReward = 50L,
        isCompleted = false,
        icon = "❄️",
    ),
    DailyQuest(
        id = QuestIds.CENTURY_SAVER,
        title = "Century Saver",
        description = "Bank 60 minutes of saved time today",
        xpReward = 200L,
        goldReward = 40L,
        isCompleted = false,
        icon = "⏳",
    ),
    DailyQuest(
        id = QuestIds.BUDGET_GUARDIAN,
        title = "Budget Guardian",
        description = "Keep all distraction apps under 30 minutes today",
        xpReward = 250L,
        goldReward = 60L,
        isCompleted = false,
        icon = "🧱",
    ),
    DailyQuest(
        id = QuestIds.MASTER_BUILDER,
        title = "Master Builder",
        description = "Construct two buildings in one day",
        xpReward = 300L,
        goldReward = 70L,
        isCompleted = false,
        icon = "🏗️",
    ),
    DailyQuest(
        id = QuestIds.DAWN_DISCIPLINE,
        title = "Dawn Discipline",
        description = "No distraction apps before 9am",
        xpReward = 150L,
        goldReward = 30L,
        isCompleted = false,
        icon = "🌅",
    ),
)

/** How many quests are active on any given day. */
const val QUESTS_PER_DAY = 3

/**
 * The quests active on [date], chosen deterministically: a sliding window of
 * [QUESTS_PER_DAY] over [questCatalog] that advances one position per day. Every quest is
 * active 3 days in every 8, and consecutive days share 2 of 3 — so it feels like a rotation,
 * not a reshuffle. Keyed by the local date, so every device shows the same set for "today".
 */
fun questsForDay(date: LocalDate): List<DailyQuest> {
    val start = date.toEpochDays().mod(questCatalog.size)
    return List(QUESTS_PER_DAY) { i -> questCatalog[(start + i).mod(questCatalog.size)] }
}
