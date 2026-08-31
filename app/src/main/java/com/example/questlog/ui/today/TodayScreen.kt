package com.example.questlog.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.questlog.theme.QuestIcons
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestSpacing
import com.example.questlog.theme.QuestType
import com.example.questlog.ui.common.Hairline
import com.example.questlog.ui.common.Pill
import com.example.questlog.ui.common.QuestScaffold
import com.example.questlog.ui.dashboard.DashboardUiState

@Composable
fun TodayScreen(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onOpenPaywall: () -> Unit,
    onOpenRealm: () -> Unit,
    onOpenBlocklist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = QuestLogTheme.colors
    QuestScaffold(
        modifier = modifier,
        header = {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Questlog", style = QuestType.wordmark, color = c.inkPrimary)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(QuestSpacing.sm),
                ) {
                    if (state.isPremium) {
                        Pill("Pro", filled = true, onClick = null)
                    } else {
                        Pill("Get Pro", filled = false, onClick = onOpenPaywall)
                    }
                    IconButton(
                        onClick = onOpenBlocklist,
                        modifier = Modifier.semantics { contentDescription = "Distractions" },
                    ) {
                        Icon(QuestIcons.Settings, contentDescription = null, tint = c.inkMuted)
                    }
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.semantics {
                            contentDescription = if (state.isLoading) "Refreshing" else "Refresh"
                        },
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                Modifier.size(20.dp),
                                color = c.earned,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(QuestIcons.Refresh, contentDescription = null, tint = c.inkMuted)
                        }
                    }
                }
            }
        },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(QuestSpacing.lg),
        ) {
            Hairline()
            TodayHero(state.stats)
            if (state.isPremium) {
                Text(
                    if (state.stats.streakFreezeReady) "Shield ready" else "Shield recharging",
                    style = QuestType.caption,
                    color = if (state.stats.streakFreezeReady) c.earned else c.inkMuted,
                )
            }
            Hairline()
            LevelBar(state.stats)
            if (state.blockedAppCount > 0) {
                Text(
                    "${state.blockedAppCount} ${if (state.blockedAppCount == 1) "app" else "apps"} guarded".uppercase(),
                    style = QuestType.caption,
                    color = c.inkMuted,
                )
            }
            Hairline()
            QuestLedger(state.dailyQuests)
            Hairline()
            RealmStrip(state.cityTiles, onOpen = onOpenRealm)
            Spacer(Modifier.height(QuestSpacing.xxl))
        }
    }
}

private fun previewState(isPremium: Boolean) = DashboardUiState(
    isLoading = false,
    stats = fakeStats(),
    cityTiles = fakeTiles(),
    dailyQuests = listOf(
        com.questlog.domain.model.DailyQuest("a", "Budget Guardian", "Distraction apps under 30 min", 250, 60, true, ""),
        com.questlog.domain.model.DailyQuest("b", "Master Builder", "Build two in one day", 300, 70, true, ""),
        com.questlog.domain.model.DailyQuest("c", "Dawn Discipline", "Nothing before 9am", 150, 30, false, ""),
    ),
    blockedAppCount = 9,
    isPremium = isPremium,
)

@androidx.compose.ui.tooling.preview.Preview(name = "Today free")
@androidx.compose.ui.tooling.preview.Preview(name = "Today dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TodayPreview() {
    QuestLogTheme {
        TodayScreen(
            state = previewState(isPremium = false),
            onRefresh = {}, onOpenPaywall = {}, onOpenRealm = {}, onOpenBlocklist = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Today premium")
@Composable
private fun TodayPremiumPreview() {
    QuestLogTheme {
        TodayScreen(
            state = previewState(isPremium = true),
            onRefresh = {}, onOpenPaywall = {}, onOpenRealm = {}, onOpenBlocklist = {},
        )
    }
}
