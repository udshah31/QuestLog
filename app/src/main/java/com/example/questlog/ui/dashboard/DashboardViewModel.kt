package com.example.questlog.ui.dashboard

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.questlog.billing.BillingManager
import com.example.questlog.billing.MilestoneOfferStore
import com.example.questlog.billing.ProOffer
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

enum class PaywallReason { Manual, Milestone }

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
    val blockedAppCount: Int = 0,
    val isPremium: Boolean = false,
    val showPaywall: Boolean = false,
    val paywallReason: PaywallReason = PaywallReason.Manual,
    val proOffer: ProOffer? = null,
    val offerLoading: Boolean = false,
    val purchasing: Boolean = false,
    val snackbarMessage: String? = null,
)

sealed interface DashboardIntent {
    object Refresh : DashboardIntent
    data class Purchase(val tile: CityTile) : DashboardIntent
    object OpenPaywall : DashboardIntent
    object DismissPaywall : DashboardIntent
    object UnlockProDemo : DashboardIntent
    data class BuyPro(val activity: Activity) : DashboardIntent
    object DismissSnackbar : DashboardIntent
}

class DashboardViewModel(
    private val getDashboardStats: GetDashboardStatsUseCase,
    private val calculateDetoxRewards: CalculateDetoxRewardsUseCase,
    private val detoxMonitor: DetoxMonitorFlow,
    private val purchaseBuilding: PurchaseBuildingUseCase,
    private val dailyQuestRepo: DailyQuestRepository,
    private val billingManager: BillingManager,
    private val milestoneStore: MilestoneOfferStore,
) : ViewModel() {

    companion object {
        const val MILESTONE_DAYS = 7
    }

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        // Collect reactive domain stats, quests, and billing state
        viewModelScope.launch {
            combine(
                getDashboardStats(),
                dailyQuestRepo.observeToday(),
                billingManager.isPremium,
                billingManager.entitlementsKnown,
            ) { dashboardState, quests, isPremium, entitlementsKnown ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        stats = dashboardState.stats,
                        cityTiles = dashboardState.cityTiles,
                        dailyQuests = quests,
                        blockedAppCount = dashboardState.blockedAppCount,
                        isPremium = isPremium,
                    )
                }
                maybeOfferMilestone(dashboardState.stats.consecutiveDetoxDays, isPremium, entitlementsKnown)
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
                                it.copy(snackbarMessage = "Constructed ${tile.displayName}.")
                            }
                        }
                        is PurchaseResult.InsufficientFunds -> {
                            _uiState.update {
                                it.copy(snackbarMessage = "Not enough gold. Reclaim more time to earn it.")
                            }
                        }
                        is PurchaseResult.PremiumRequired -> openPaywall(PaywallReason.Manual)
                        is PurchaseResult.AlreadyOwned -> {
                            _uiState.update {
                                it.copy(snackbarMessage = "${tile.displayName} is already built.")
                            }
                        }
                    }
                }
            }

            is DashboardIntent.OpenPaywall -> openPaywall(PaywallReason.Manual)

            is DashboardIntent.DismissPaywall -> {
                _uiState.update { it.copy(showPaywall = false) }
            }

            is DashboardIntent.BuyPro -> {
                val state = _uiState.value
                val offer = state.proOffer ?: return
                if (state.purchasing) return // double-tap guard
                _uiState.update { it.copy(purchasing = true) }
                billingManager.purchasePackage(
                    activity = intent.activity,
                    pkg = offer.pkg,
                    onSuccess = {
                        // Entitlement reaches isPremium via BillingManager's listener.
                        _uiState.update {
                            it.copy(purchasing = false, showPaywall = false, snackbarMessage = "Welcome to QuestLog Pro.")
                        }
                    },
                    onError = { _, userCancelled ->
                        _uiState.update {
                            it.copy(
                                purchasing = false,
                                snackbarMessage = if (userCancelled) it.snackbarMessage else "Purchase didn't go through.",
                            )
                        }
                    },
                )
            }

            is DashboardIntent.UnlockProDemo -> {
                billingManager.setDebugPremium(true)
                _uiState.update {
                    it.copy(
                        isPremium = true,
                        showPaywall = false,
                        snackbarMessage = "QuestLog Pro unlocked. All buildings available."
                    )
                }
            }

            is DashboardIntent.DismissSnackbar -> {
                _uiState.update { it.copy(snackbarMessage = null) }
            }
        }
    }

    /** First 7-day streak for a known-free player: open the trial paywall, once ever. */
    private fun maybeOfferMilestone(streak: Int, isPremium: Boolean, entitlementsKnown: Boolean) {
        if (streak < MILESTONE_DAYS || isPremium || !entitlementsKnown) return
        // Don't hijack a paywall the player opened themselves; try again on a later emission.
        if (_uiState.value.showPaywall || milestoneStore.shown) return
        openPaywall(PaywallReason.Milestone) // marks the milestone shown
    }

    private fun openPaywall(reason: PaywallReason) {
        // Seeing any paywall at 7+ days consumes the milestone — no second one after "Maybe later".
        if (_uiState.value.stats.consecutiveDetoxDays >= MILESTONE_DAYS && !milestoneStore.shown) {
            milestoneStore.markShown()
        }
        _uiState.update { it.copy(showPaywall = true, paywallReason = reason, offerLoading = true) }
        viewModelScope.launch {
            val offer = billingManager.loadProOffer()
            _uiState.update { it.copy(proOffer = offer, offerLoading = false) }
        }
    }
}
