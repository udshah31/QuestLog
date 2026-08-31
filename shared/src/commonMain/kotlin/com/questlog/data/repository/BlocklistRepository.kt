package com.questlog.data.repository

import com.questlog.data.local.dao.BlocklistDao
import com.questlog.data.local.entity.BlockedAppEntity
import com.questlog.domain.model.BlockedApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The user's distraction list. A row in `blocked_app` means the app is a
 * distraction; `dailyLimitMs` is its allowance (0 = fully blocked). `open` so
 * tests can stub it.
 */
open class BlocklistRepository(private val dao: BlocklistDao) {

    open fun observeBlockedApps(): Flow<List<BlockedApp>> =
        dao.observeAll().map { rows -> rows.map { it.toModel() } }

    open suspend fun current(): List<BlockedApp> = dao.getAll().map { it.toModel() }

    /** Enable/disable an app as a distraction. Enabling keeps any existing limit. */
    open suspend fun setBlocked(packageName: String, blocked: Boolean) {
        if (blocked) {
            if (dao.get(packageName) == null) dao.upsert(BlockedAppEntity(packageName, 0L))
        } else {
            dao.delete(packageName)
        }
    }

    /** Set an app's daily allowance. A limit on an unblocked app blocks it. */
    open suspend fun setLimit(packageName: String, dailyLimitMs: Long) {
        dao.upsert(BlockedAppEntity(packageName, dailyLimitMs.coerceAtLeast(0L)))
    }

    private fun BlockedAppEntity.toModel() = BlockedApp(packageName, dailyLimitMs)
}
