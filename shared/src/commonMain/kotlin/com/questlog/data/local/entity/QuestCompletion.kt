package com.questlog.data.local.entity

import androidx.room.Entity

/**
 * One row per quest completed on a given day. A row's existence means the quest was
 * completed *and* its reward was granted; there is no separate "claimed" state.
 */
@Entity(tableName = "quest_completions", primaryKeys = ["date", "questId"])
data class QuestCompletion(
    /** ISO-8601 local date, e.g. "2026-08-28". */
    val date: String,
    val questId: String,
    val completedAt: Long,
)
