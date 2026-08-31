package com.example.questlog.ui.blocklist

import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.questlog.data.InstalledAppsProvider
import com.questlog.data.repository.BlocklistRepository
import com.questlog.domain.model.BlockedApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppRow(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val blocked: Boolean,
    val dailyLimitMs: Long,
)

data class BlocklistUiState(
    val loading: Boolean = true,
    val permissionGranted: Boolean = true,
    val rows: List<AppRow> = emptyList(),
    val query: String = "",
)

sealed interface BlocklistIntent {
    data class ToggleBlocked(val packageName: String) : BlocklistIntent
    data class SetLimit(val packageName: String, val dailyLimitMs: Long) : BlocklistIntent
    data class SetQuery(val query: String) : BlocklistIntent
    data object RecheckPermission : BlocklistIntent

    /** Re-group blocked apps to the top. Fired on screen entry, never on a toggle. */
    data object Regroup : BlocklistIntent
}

class BlocklistViewModel(
    private val blocklistRepo: BlocklistRepository,
    private val installedApps: InstalledAppsProvider,
    private val isUsageAccessGranted: () -> Boolean,
) : ViewModel() {

    private data class AppMeta(val packageName: String, val label: String, val icon: Drawable?)

    private val installed = MutableStateFlow<List<AppMeta>?>(null)
    private val query = MutableStateFlow("")
    private val permission = MutableStateFlow(isUsageAccessGranted())

    /**
     * The row order, by package name. Blocked-first then A–Z, recomputed only on
     * screen entry ([BlocklistIntent.Regroup]) — never on a toggle, so a row the
     * user just flipped stays where their finger is.
     */
    private val order = MutableStateFlow<List<String>>(emptyList())

    init {
        viewModelScope.launch {
            installed.value = installedApps.launchableApps()
                .map { AppMeta(it.packageName, it.label, it.icon) }
            recomputeOrder()
        }
    }

    private suspend fun recomputeOrder() {
        val apps = installed.value ?: return
        val blockedPkgs = blocklistRepo.current().mapTo(mutableSetOf()) { it.packageName }
        order.value = apps
            .sortedWith(
                compareByDescending<AppMeta> { it.packageName in blockedPkgs }
                    .thenBy { it.label.lowercase() },
            )
            .map { it.packageName }
    }

    val uiState: StateFlow<BlocklistUiState> =
        combine(
            installed,
            order,
            blocklistRepo.observeBlockedApps(),
            query,
            permission,
        ) { apps, ord, blocked, q, granted ->
            if (apps == null) {
                BlocklistUiState(loading = true, permissionGranted = granted, query = q)
            } else {
                val metaByPkg = apps.associateBy { it.packageName }
                val byPkg: Map<String, BlockedApp> = blocked.associateBy { it.packageName }
                // Follow the frozen order; before the first recompute, fall back to the
                // provider's own (alphabetical) order so nothing flickers.
                val pkgOrder = ord.ifEmpty { apps.map { it.packageName } }
                val rows = pkgOrder
                    .mapNotNull { pkg ->
                        val meta = metaByPkg[pkg] ?: return@mapNotNull null
                        val b = byPkg[pkg]
                        AppRow(
                            packageName = pkg,
                            label = meta.label,
                            icon = meta.icon,
                            blocked = b != null,
                            dailyLimitMs = b?.dailyLimitMs ?: 0L,
                        )
                    }
                    .filter { q.isBlank() || it.label.contains(q, ignoreCase = true) }
                BlocklistUiState(loading = false, permissionGranted = granted, rows = rows, query = q)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, BlocklistUiState())

    fun onIntent(intent: BlocklistIntent) {
        when (intent) {
            is BlocklistIntent.ToggleBlocked -> viewModelScope.launch {
                val currentlyBlocked = blocklistRepo.current().any { it.packageName == intent.packageName }
                blocklistRepo.setBlocked(intent.packageName, blocked = !currentlyBlocked)
            }
            is BlocklistIntent.SetLimit -> viewModelScope.launch {
                blocklistRepo.setLimit(intent.packageName, intent.dailyLimitMs)
            }
            is BlocklistIntent.SetQuery -> query.value = intent.query
            BlocklistIntent.RecheckPermission -> permission.value = isUsageAccessGranted()
            BlocklistIntent.Regroup -> viewModelScope.launch { recomputeOrder() }
        }
    }
}
