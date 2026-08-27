package com.example.questlog.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.questlog.billing.BillingManager
import com.example.questlog.ui.components.DailyQuest
import com.questlog.domain.model.CityTile
import com.questlog.domain.model.PlayerStats
import com.questlog.domain.usecase.CalculateDetoxRewardsUseCase
import com.questlog.domain.usecase.GetDashboardStatsUseCase
import com.questlog.domain.usecase.PurchaseBuildingUseCase
import com.questlog.domain.usecase.PurchaseResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    private val purchaseBuilding: PurchaseBuildingUseCase,
    private val billingManager: BillingManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DashboardUiState(
            dailyQuests = listOf(
                DailyQuest(
                    id = "q1",
                    title = "Digital Fasting",
                    description = "Stay off Instagram for 60 minutes",
                    xpReward = 150L,
                    goldReward = 30L,
                    isCompleted = false,
                    icon = "🧘",
                ),
                DailyQuest(
                    id = "q2",
                    title = "Deep Focus Shield",
                    description = "Zero doomscroll between 9am - 12pm",
                    xpReward = 300L,
                    goldReward = 80L,
                    isCompleted = true,
                    icon = "🛡️",
                ),
                DailyQuest(
                    id = "q3",
                    title = "Sanctuary Builder",
                    description = "Construct any building in your realm",
                    xpReward = 200L,
                    goldReward = 50L,
                    isCompleted = false,
                    icon = "🔨",
                ),
            )
        )
    )
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        // Collect reactive domain stats and billing state
        viewModelScope.launch {
            combine(
                getDashboardStats(),
                billingManager.isPremium,
            ) { dashboardState, isPremium ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        stats = dashboardState.stats,
                        cityTiles = dashboardState.cityTiles,
                        isPremium = isPremium,
                    )
                }
            }.collect {}
        }

        // Trigger initial calculation
        onIntent(DashboardIntent.Refresh)
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
