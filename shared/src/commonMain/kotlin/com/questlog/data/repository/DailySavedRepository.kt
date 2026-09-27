package com.questlog.data.repository

import com.questlog.data.local.dao.DailySavedDao
import com.questlog.data.local.entity.DailySaved
import kotlinx.coroutines.flow.Flow

class DailySavedRepository(private val dao: DailySavedDao) {
    suspend fun record(date: String, savedMs: Long) = dao.upsert(DailySaved(date, savedMs))
    fun observeSince(fromDate: String): Flow<List<DailySaved>> = dao.observeSince(fromDate)
    fun observeBestMsBefore(date: String): Flow<Long?> = dao.observeBestMsBefore(date)
}
