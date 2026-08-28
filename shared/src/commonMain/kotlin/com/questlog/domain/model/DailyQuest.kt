package com.questlog.domain.model

/** A quest shown on the dashboard for the current day. */
data class DailyQuest(
    val id: String,
    val title: String,
    val description: String,
    val xpReward: Long,
    val goldReward: Long,
    val isCompleted: Boolean,
    val icon: String,
)
