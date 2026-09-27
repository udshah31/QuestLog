package com.questlog.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One judged Mindful Unlock. [graceMs] is what it earned (0 if not purposeful).
 * The player's typed reason is deliberately not stored.
 */
@Entity(tableName = "mindful_unlock", indices = [Index("date")])
data class MindfulUnlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val packageName: String,
    val category: String,
    val purposeful: Double,
    val graceMs: Long,
    val createdAt: Long,
)
