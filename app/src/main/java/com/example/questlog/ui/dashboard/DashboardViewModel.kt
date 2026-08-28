package com.example.questlog.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.questlog.billing.BillingManager
import com.questlog.data.repository.DailyQuestRepository
import com.questlog.domain.model.CityTile
import com.questlog.domain.model.DailyQuest
import com.questlog.domain.model.PlayerStats
import com.questlog.domain.usecase.CalculateDetoxRewardsUseCase
import com.questlog.domain.usecase.DetoxMonitorFlow
import com.questlog.domain.usecase.GetDashboardStatsUseCase
import com.questlog.domain.usecase.PurchaseBuildingUseCase
import com.questlog.domain.usecase.PurchaseResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = true,
    val stats: PlayerStats = PlayerStats(
        level = 1,
        xp = 0L,
        xpToNextLevel = 100L,
        gold = 0L,
        gems = 0L,
        consecutiveDetoxDays = 0,
        streakMultiplier = 1.0f,
        todaySavedMs = 0L,
        streakFreezeReady = true,
    ),
    val cityTiles: List<CityTile> = emptyList(),
    val dailyQuests: List<DailyQuest> = emptyList(),
    val isPremium: Boolean = false,
    val showPaywall: Boolean = false,
    val snackbarMessage: String? = null,
)

sealed interface DashboardIntent {
    object Refresh : DashboardIntent
    data class Purchase(val tile: CityTile) : DashboardIntent
    object OpenPaywall : DashboardIntent
    object DismissPaywall : DashboardIntent
    object UnlockProDemo : DashboardIntent
    object DismissSnackbar : DashboardIntent
}

class DashboardViewModel(
    private val getDashboardStats: GetDashboardStatsUseCase,
    private val calculateDetoxRewards: CalculateDetoxRewardsUseCase,
    private val detoxMonitor: DetoxMonitorFlow,
    private val purchaseBuilding: PurchaseBuildingUseCase,
    private val dailyQuestRepo: DailyQuestRepository,
    private val billingManager: BillingManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        // Collect reactive domain stats, quests, and billing state
        viewModelScope.launch {
            combine(
                getDashboardStats(),
                dailyQuestRepo.observeToday(),
                billingManager.isPremium,
            ) { dashboardState, quests, isPremium ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        stats = dashboardState.stats,
                        cityTiles = dashboardState.cityTiles,
                        dailyQuests = quests,
                        isPremium = isPremium,
                    )
                }
            }.collect {}
        }

        // Poll screen-time in the background: an initial tick on start, then every
        // interval. Results reach the UI reactively via getDashboardStats() above.
        viewModelScope.launch {
            detoxMonitor()
                .catch { /* keep the dashboard alive if the monitor ever fails hard */ }
                .collect {}
        }
    }

    fun onIntent(intent: DashboardIntent) {
        when (intent) {
            is DashboardIntent.Refresh -> {
                viewModelScope.launch {
                    _uiState.update { it.copy(isLoading = true) }
                    try {
                        calculateDetoxRewards()
                    } catch (_: Exception) {
                        // Offline or permission fallback
                    } finally {
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
            }

            is DashboardIntent.Purchase -> {
                viewModelScope.launch {
                    val tile = intent.tile
                    val isPremium = _uiState.value.isPremium
                    when (val result = purchaseBuilding(tile, isPremium)) {
                        is PurchaseResult.Success -> {
                            _uiState.update {
                                it.copy(snackbarMessage = "🎉 Successfully constructed ${tile.displayName}!")
                            }
                        }
                        is PurchaseResult.InsufficientFunds -> {
                            _uiState.update {
                                it.copy(snackbarMessage = "⚠️ Not enough gold! Avoid more screen time to earn gold.")
                            }
                        }
                        is PurchaseResult.PremiumRequired -> {
                            _uiState.update { it.copy(showPaywall = true) }
                        }
                        is PurchaseResult.AlreadyOwned -> {
                            _uiState.update {
                                it.copy(snackbarMessage = "${tile.displayName} is already built!")
                            }
                        }
                    }
                }
            }

            is DashboardIntent.OpenPaywall -> {
                _uiState.update { it.copy(showPaywall = true) }
            }

            is DashboardIntent.DismissPaywall -> {
                _uiState.update { it.copy(showPaywall = false) }
            }

            is DashboardIntent.UnlockProDemo -> {
                billingManager.setDebugPremium(true)
                _uiState.update {
                    it.copy(
                        isPremium = true,
                        showPaywall = false,
                        snackbarMessage = "👑 Welcome to QuestLog Pro! All premium buildings unlocked."
                    )
                }
            }

            is DashboardIntent.DismissSnackbar -> {
                _uiState.update { it.copy(snackbarMessage = null) }
            }
        }
    }
}
