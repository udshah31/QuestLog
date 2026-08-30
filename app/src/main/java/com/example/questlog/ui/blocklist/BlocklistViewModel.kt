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

    init {
        viewModelScope.launch {
            installed.value = installedApps.launchableApps()
                .map { AppMeta(it.packageName, it.label, it.icon) }
        }
    }

    val uiState: StateFlow<BlocklistUiState> =
        combine(
            installed,
            blocklistRepo.observeBlockedApps(),
            query,
            permission,
        ) { apps, blocked, q, granted ->
            if (apps == null) {
                BlocklistUiState(loading = true, permissionGranted = granted, query = q)
            } else {
                val byPkg: Map<String, BlockedApp> = blocked.associateBy { it.packageName }
                val rows = apps
                    .filter { q.isBlank() || it.label.contains(q, ignoreCase = true) }
                    .map { meta ->
                        val b = byPkg[meta.packageName]
                        AppRow(
                            packageName = meta.packageName,
                            label = meta.label,
                            icon = meta.icon,
                            blocked = b != null,
                            dailyLimitMs = b?.dailyLimitMs ?: 0L,
                        )
                    }
                    .sortedWith(compareByDescending<AppRow> { it.blocked }.thenBy { it.label.lowercase() })
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
        }
    }
}
