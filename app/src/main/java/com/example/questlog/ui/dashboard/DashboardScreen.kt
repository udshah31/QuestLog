package com.example.questlog.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.questlog.theme.QuestArcane
import com.example.questlog.theme.QuestEmerald
import com.example.questlog.theme.QuestGold
import com.example.questlog.theme.QuestSlateBorder
import com.example.questlog.theme.QuestSlateCard
import com.example.questlog.theme.QuestSlateDark
import com.example.questlog.theme.QuestTextPrimary
import com.example.questlog.theme.QuestTextSecondary
import com.example.questlog.ui.components.CityGrid
import com.example.questlog.ui.components.DailyQuestBanner
import com.example.questlog.ui.components.PaywallModal
import com.example.questlog.ui.components.StatsCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onIntent(DashboardIntent.DismissSnackbar)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = QuestSlateDark,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "⚔️ QUESTLOG",
                            color = QuestGold,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            letterSpacing = 1.sp,
                        )
                    }
                },
                actions = {
                    // Pro Badge or Upgrade button
                    val proBadgeBrush = if (uiState.isPremium) {
                        Brush.linearGradient(listOf(QuestGold, QuestArcane))
                    } else {
                        Brush.linearGradient(listOf(QuestSlateBorder.copy(alpha = 0.5f), QuestSlateBorder.copy(alpha = 0.5f)))
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(proBadgeBrush)
                            .border(
                                1.dp,
                                if (uiState.isPremium) QuestGold else QuestSlateBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                if (!uiState.isPremium) {
                                    viewModel.onIntent(DashboardIntent.OpenPaywall)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (uiState.isPremium) "👑 PRO ACTIVE" else "⚡ GET PRO",
                            color = if (uiState.isPremium) QuestSlateDark else QuestGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Manual Sync / Refresh button
                    IconButton(
                        onClick = { viewModel.onIntent(DashboardIntent.Refresh) }
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = QuestGold,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(text = "🔄", fontSize = 18.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = QuestSlateDark,
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 1. Stats & Progress Card
            StatsCard(stats = uiState.stats, isPremium = uiState.isPremium)

            // 2. Daily Quests
            DailyQuestBanner(quests = uiState.dailyQuests)

            // 3. Sanctuary Realm City Grid
            CityGrid(
                tiles = uiState.cityTiles,
                onTileClick = { tile ->
                    viewModel.onIntent(DashboardIntent.Purchase(tile))
                },
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Paywall Modal
        if (uiState.showPaywall) {
            PaywallModal(
                onDismiss = { viewModel.onIntent(DashboardIntent.DismissPaywall) },
                onUnlockPro = { viewModel.onIntent(DashboardIntent.UnlockProDemo) },
            )
        }
    }
}
