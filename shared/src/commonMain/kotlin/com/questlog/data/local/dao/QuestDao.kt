package com.questlog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.questlog.data.local.entity.QuestCompletion
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestDao {
    /** Returns the new rowId, or -1 if a completion for (date, questId) already existed. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(completion: QuestCompletion): Long

    @Query("SELECT questId FROM quest_completions WHERE date = :date")
    fun observeCompletedIds(date: String): Flow<List<String>>

    @Query("SELECT questId FROM quest_completions WHERE date = :date")
    suspend fun completedIds(date: String): List<String>
}
