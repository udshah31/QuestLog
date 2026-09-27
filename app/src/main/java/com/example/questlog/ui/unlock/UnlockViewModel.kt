package com.example.questlog.ui.unlock

import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.questlog.data.InstalledAppsProvider
import com.example.questlog.unlock.IntentJudge
import com.example.questlog.unlock.Verdict
import com.questlog.data.repository.BlocklistRepository
import com.questlog.data.repository.MindfulUnlockRepository
import com.questlog.domain.unlock.MindfulUnlockRule
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UnlockApp(val packageName: String, val label: String, val icon: Drawable?)

enum class UnlockPhase { Pick, Reason, Checking, Granted, Drifting, Unavailable }

data class UnlockUiState(
    val apps: List<UnlockApp> = emptyList(),
    val selected: UnlockApp? = null,
    val reason: String = "",
    val phase: UnlockPhase = UnlockPhase.Pick,
    val isPremium: Boolean = false,
    val usedToday: Int = 0,
) {
    val freeLeft: Int get() = (MindfulUnlockRule.FREE_UNLOCKS_PER_DAY - usedToday).coerceAtLeast(0)
}

sealed interface UnlockIntent {
    data class Select(val app: UnlockApp) : UnlockIntent
    data class SetReason(val text: String) : UnlockIntent
    data object Check : UnlockIntent
    data object Open : UnlockIntent
    data object NotNow : UnlockIntent
    /** Fired on screen entry: back to the app list, fresh quota. */
    data object Reset : UnlockIntent
}

sealed interface UnlockEvent {
    data class Launch(val packageName: String) : UnlockEvent
    data object OpenPaywall : UnlockEvent
    data object Close : UnlockEvent
}

class UnlockViewModel(
    private val blocklistRepo: BlocklistRepository,
    private val installedApps: InstalledAppsProvider,
    private val judge: IntentJudge,
    private val unlocks: MindfulUnlockRepository,
    private val isPremium: () -> Boolean,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UnlockUiState())
    val uiState: StateFlow<UnlockUiState> = _uiState.asStateFlow()

    private val _events = Channel<UnlockEvent>(Channel.BUFFERED)
    val events: Flow<UnlockEvent> = _events.receiveAsFlow()

    init { load() }

    private fun load() = viewModelScope.launch {
        val blocked = blocklistRepo.current().mapTo(mutableSetOf()) { it.packageName }
        val apps = installedApps.launchableApps()
            .filter { it.packageName in blocked }
            .map { UnlockApp(it.packageName, it.label, it.icon) }
        _uiState.update { it.copy(apps = apps, isPremium = isPremium(), usedToday = unlocks.countToday()) }
    }

    fun onIntent(intent: UnlockIntent) {
        when (intent) {
            is UnlockIntent.Select -> _uiState.update { it.copy(selected = intent.app, reason = "", phase = UnlockPhase.Reason) }
            is UnlockIntent.SetReason -> _uiState.update { it.copy(reason = intent.text.take(MAX_REASON)) }
            UnlockIntent.Check -> check()
            UnlockIntent.Open -> _uiState.value.selected?.let { _events.trySend(UnlockEvent.Launch(it.packageName)) }
            UnlockIntent.NotNow -> _events.trySend(UnlockEvent.Close)
            UnlockIntent.Reset -> {
                _uiState.update { it.copy(selected = null, reason = "", phase = UnlockPhase.Pick) }
                load()
            }
        }
    }

    private fun check() {
        val s = _uiState.value
        val app = s.selected ?: return
        val reason = s.reason.trim()
        if (reason.isEmpty() || s.phase == UnlockPhase.Checking) return
        _uiState.update { it.copy(phase = UnlockPhase.Checking) } // synchronous: blocks a double tap
        viewModelScope.launch {
            val premium = isPremium() // read live: a purchase since the screen opened counts
            if (!premium && unlocks.countToday() >= MindfulUnlockRule.FREE_UNLOCKS_PER_DAY) {
                _uiState.update { it.copy(phase = UnlockPhase.Reason, isPremium = false) }
                _events.send(UnlockEvent.OpenPaywall)
                return@launch
            }
            val phase = when (val v = judge.judge(app.label, reason)) {
                is Verdict.Judged ->
                    if (unlocks.record(app.packageName, v.category, v.purposeful) > 0) UnlockPhase.Granted
                    else UnlockPhase.Drifting
                Verdict.Unavailable -> UnlockPhase.Unavailable
            }
            _uiState.update { it.copy(phase = phase, isPremium = premium, usedToday = unlocks.countToday()) }
        }
    }

    private companion object { const val MAX_REASON = 200 }
}
