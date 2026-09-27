package com.example.questlog.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.questlog.domain.model.ProgressStats
import com.questlog.domain.usecase.GetProgressStatsUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ProgressUiState(val isLoading: Boolean = true, val stats: ProgressStats? = null)

class ProgressViewModel(getProgressStats: GetProgressStatsUseCase) : ViewModel() {
    val uiState: StateFlow<ProgressUiState> =
        getProgressStats()
            .map { ProgressUiState(isLoading = false, stats = it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())
}
