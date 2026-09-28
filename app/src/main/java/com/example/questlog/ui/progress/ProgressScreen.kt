package com.example.questlog.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.questlog.theme.QuestIcons
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestShapes
import com.example.questlog.theme.QuestSpacing
import com.example.questlog.theme.QuestType
import com.example.questlog.ui.common.Hairline
import com.example.questlog.ui.common.QuestScaffold
import com.example.questlog.ui.format.levelTitle
import com.example.questlog.ui.share.renderShareCard
import com.example.questlog.ui.share.shareCaption
import com.example.questlog.ui.share.shareCard
import com.questlog.domain.model.CityTile
import com.questlog.domain.model.DaySaved
import com.questlog.domain.model.ProgressStats
import com.questlog.util.TimeConversion
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate

@Composable
fun ProgressScreen(state: ProgressUiState, tiles: List<CityTile>, onBack: () -> Unit) {
    val c = QuestLogTheme.colors
    val context = LocalContext.current
    QuestScaffold(
        header = {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(QuestIcons.Back, contentDescription = "Back", tint = c.inkPrimary) }
                Text("Progress", style = QuestType.screenTitle, color = c.inkPrimary, modifier = Modifier.weight(1f))
                state.stats?.let { s ->
                    IconButton(onClick = {
                        shareCard(context, renderShareCard(context, s, tiles, c), shareCaption(s.reclaimedAllTimeMs))
                    }) { Icon(QuestIcons.Share, contentDescription = "Share progress", tint = c.inkPrimary) }
                }
            }
        },
    ) {
        Hairline()
        val s = state.stats
        if (s == null) {
            DelayedLoading()
            return@QuestScaffold
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(top = QuestSpacing.md),
            verticalArrangement = Arrangement.spacedBy(QuestSpacing.lg),
        ) {
            Column {
                Text("Reclaimed all-time".uppercase(), style = QuestType.label, color = c.inkMuted)
                Text(reclaimedLine(s.reclaimedAllTimeMs), style = QuestType.display, color = c.inkPrimary)
            }
            Hairline()
            Column(verticalArrangement = Arrangement.spacedBy(QuestSpacing.md)) {
                Row(horizontalArrangement = Arrangement.spacedBy(QuestSpacing.md)) {
                    StatTile("Streak", "${s.streakDays}d", Modifier.weight(1f), valueColor = c.earned)
                    StatTile("Best day", reclaimedLine(s.bestDayMs), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(QuestSpacing.md)) {
                    StatTile("Apps guarded", "${s.appsGuarded}", Modifier.weight(1f))
                    StatTile("Quests cleared", "${s.questsCleared}", Modifier.weight(1f))
                }
            }
            Hairline()
            LevelBlock(s)
            Hairline()
            ReclaimedChart(s.last7Days)
            Spacer(Modifier.height(QuestSpacing.xxl))
        }
    }
}

/** Shown only if loading outlasts 300 ms, so a normal one-frame load never flashes. */
@Composable
private fun DelayedLoading() {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300)
        show = true
    }
    if (show) {
        Text(
            "Loading…",
            style = QuestType.caption,
            color = QuestLogTheme.colors.inkMuted,
            modifier = Modifier.padding(top = QuestSpacing.md),
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = QuestLogTheme.colors.inkPrimary) {
    val c = QuestLogTheme.colors
    Column(
        modifier
            .clip(QuestShapes.small)
            .border(1.dp, c.rule, QuestShapes.small)
            .heightIn(min = 72.dp)
            .padding(QuestSpacing.md)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(label.uppercase(), style = QuestType.caption, color = c.inkMuted)
        Spacer(Modifier.height(QuestSpacing.sm))
        Text(value, style = QuestType.display.copy(fontSize = 26.sp, lineHeight = 28.sp), color = valueColor)
    }
}

@Composable
private fun LevelBlock(s: ProgressStats) {
    val c = QuestLogTheme.colors
    val progress = TimeConversion.xpProgress(s.xp)
    Column(verticalArrangement = Arrangement.spacedBy(QuestSpacing.sm)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Level ${s.level} · ${levelTitle(s.level)}", style = QuestType.bodySmall, color = c.inkMuted)
            Text("${(progress * 100).toInt()}%", style = QuestType.serifNumeral, color = c.inkMuted)
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(c.rule)) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(c.inkSecondary))
        }
        Text(
            "${xpToGo(s.xp, s.xpToNextLevel)} XP to level ${s.level + 1}".uppercase(),
            style = QuestType.caption,
            color = c.inkMuted,
        )
    }
}

@Composable
private fun ReclaimedChart(days: List<DaySaved>) {
    val c = QuestLogTheme.colors
    Column {
        Text("Last 7 days".uppercase(), style = QuestType.label, color = c.inkMuted)
        Spacer(Modifier.height(QuestSpacing.md))
        if (showsEmptyWeek(days)) {
            Text("Your week fills in here as you reclaim time.", style = QuestType.bodySmall, color = c.inkMuted)
            return
        }
        Row(
            // One TalkBack stop for the whole chart, not the summary plus seven letters.
            Modifier.fillMaxWidth().clearAndSetSemantics {
                contentDescription = "Last 7 days: " + days.joinToString {
                    "${if (it.isToday) "today" else it.date.dayOfWeek.name.lowercase()} ${reclaimedLine(it.savedMs)}"
                }
            },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            days.forEach { d ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .width(18.dp)
                            .height((72.dp * barFraction(d.savedMs, days)).coerceAtLeast(2.dp))
                            .clip(QuestShapes.extraSmall)
                            .background(if (d.isToday) c.earned else c.inkSecondary),
                    )
                    Spacer(Modifier.height(QuestSpacing.xs))
                    Text(dayInitial(d.date.dayOfWeek), style = QuestType.caption, color = c.inkMuted)
                }
            }
        }
    }
}

private fun previewStats(week: List<Long>) = ProgressStats(
    streakDays = 6, reclaimedAllTimeMs = 14 * 3_600_000L + 20 * 60_000L, bestDayMs = 88 * 60_000L,
    appsGuarded = 7, questsCleared = 23, level = 3, xp = 420, xpToNextLevel = 600,
    last7Days = week.mapIndexed { i, ms -> DaySaved(LocalDate(2026, 9, 21 + i), ms, i == 6) },
)

@Preview(name = "Progress")
@Composable
private fun ProgressScreenPreview() {
    QuestLogTheme {
        ProgressScreen(ProgressUiState(false, previewStats(listOf(40, 62, 0, 88, 51, 30, 22).map { it * 60_000L })), tiles = emptyList(), onBack = {})
    }
}

@Preview(name = "Progress — empty week")
@Composable
private fun ProgressScreenPreview_EmptyWeek() {
    QuestLogTheme {
        ProgressScreen(ProgressUiState(false, previewStats(listOf(0, 0, 0, 0, 0, 0, 12).map { it * 60_000L })), tiles = emptyList(), onBack = {})
    }
}
