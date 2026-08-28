package com.questlog.domain.quest

import com.questlog.domain.model.DailyQuest

/** Stable ids for the fixed daily quests. */
object QuestIds {
    const val DIGITAL_FASTING = "digital_fasting"
    const val DEEP_FOCUS_SHIELD = "deep_focus_shield"
    const val SANCTUARY_BUILDER = "sanctuary_builder"
}

/** Package whose usage the Digital Fasting quest limits. */
const val DIGITAL_FASTING_PACKAGE = "com.instagram.android"

/** Max Instagram foreground time that still completes Digital Fasting. */
const val DIGITAL_FASTING_MAX_MS = 15 * 60_000L

/** Deep Focus Shield window, local wall-clock hours. */
const val DEEP_FOCUS_START_HOUR = 9
const val DEEP_FOCUS_END_HOUR = 12

/**
 * The fixed set of quests shown every day. `isCompleted` here is a placeholder —
 * [com.questlog.data.repository.DailyQuestRepository] fills it in per day.
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
        id = QuestIds.DEEP_FOCUS_SHIELD,
        title = "Deep Focus Shield",
        description = "No distraction apps between 9am and 12pm",
        xpReward = 300L,
        goldReward = 80L,
        isCompleted = false,
        icon = "🛡️",
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
)
