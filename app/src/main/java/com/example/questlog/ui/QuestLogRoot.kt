package com.example.questlog.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.questlog.ui.blocklist.BlocklistIntent
import com.example.questlog.ui.blocklist.BlocklistScreen
import com.example.questlog.ui.blocklist.BlocklistViewModel
import com.example.questlog.ui.common.reducedMotion
import com.example.questlog.ui.dashboard.DashboardIntent
import com.example.questlog.ui.dashboard.DashboardViewModel
import com.example.questlog.ui.paywall.PaywallScreen
import com.example.questlog.ui.realm.RealmScreen
import com.example.questlog.ui.today.TodayScreen
import org.koin.compose.viewmodel.koinViewModel

private enum class Screen { Today, Realm, Blocklist }

@Composable
fun QuestLogRoot(viewModel: DashboardViewModel) {
    val state by viewModel.uiState.collectAsState()
    var screen by rememberSaveable { mutableStateOf(Screen.Today) }
    val snackbarHostState = remember { SnackbarHostState() }
    val stateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val reduce = reducedMotion()

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onIntent(DashboardIntent.DismissSnackbar)
        }
    }

    BackHandler(enabled = screen != Screen.Today) { screen = Screen.Today }
    // Composed after the screen handler, so it takes priority while the paywall is up.
    BackHandler(enabled = state.showPaywall) { viewModel.onIntent(DashboardIntent.DismissPaywall) }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                if (reduce) {
                    fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                } else if (targetState != Screen.Today) {
                    (slideInHorizontally(tween(250)) { it } + fadeIn(tween(250))) togetherWith
                        (slideOutHorizontally(tween(250)) { -it / 4 } + fadeOut(tween(250)))
                } else {
                    (slideInHorizontally(tween(250)) { -it / 4 } + fadeIn(tween(250))) togetherWith
                        (slideOutHorizontally(tween(250)) { it } + fadeOut(tween(250)))
                }
            },
            label = "screen",
        ) { s ->
            stateHolder.SaveableStateProvider(s) {
                when (s) {
                    Screen.Today -> TodayScreen(
                        state = state,
                        onRefresh = { viewModel.onIntent(DashboardIntent.Refresh) },
                        onOpenPaywall = { viewModel.onIntent(DashboardIntent.OpenPaywall) },
                        onOpenRealm = { screen = Screen.Realm },
                        onOpenBlocklist = { screen = Screen.Blocklist },
                    )
                    Screen.Realm -> RealmScreen(
                        tiles = state.cityTiles,
                        gold = state.stats.gold,
                        onBack = { screen = Screen.Today },
                        onTileClick = { viewModel.onIntent(DashboardIntent.Purchase(it)) },
                    )
                    Screen.Blocklist -> {
                        val blocklistVm = koinViewModel<BlocklistViewModel>()
                        val blocklistState by blocklistVm.uiState.collectAsState()
                        val context = LocalContext.current
                        LifecycleResumeEffect(Unit) {
                            blocklistVm.onIntent(BlocklistIntent.RecheckPermission)
                            blocklistVm.onIntent(BlocklistIntent.Regroup)
                            onPauseOrDispose { }
                        }
                        BlocklistScreen(
                            state = blocklistState,
                            onIntent = blocklistVm::onIntent,
                            onBack = { screen = Screen.Today },
                            onGrantAccess = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                }
                            },
                        )
                    }
                }
            }
        }

        SnackbarHost(
            snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        )

        AnimatedVisibility(
            visible = state.showPaywall,
            enter = if (reduce) {
                fadeIn(tween(120))
            } else {
                slideInVertically(tween(250)) { it } + fadeIn(tween(250))
            },
            exit = if (reduce) {
                fadeOut(tween(120))
            } else {
                slideOutVertically(tween(250)) { it } + fadeOut(tween(250))
            },
        ) {
            PaywallScreen(
                onDismiss = { viewModel.onIntent(DashboardIntent.DismissPaywall) },
                onUnlockPro = { viewModel.onIntent(DashboardIntent.UnlockProDemo) },
            )
        }
    }
}
