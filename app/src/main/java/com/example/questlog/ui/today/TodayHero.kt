package com.example.questlog.ui.today

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestSpacing
import com.example.questlog.theme.QuestType
import com.example.questlog.ui.common.reducedMotion
import com.example.questlog.ui.format.formatMultiplier
import com.example.questlog.ui.format.formatReclaimed
import com.example.questlog.ui.format.ringFraction
import com.questlog.domain.model.PlayerStats
import com.questlog.util.DetoxBudget

@Composable
fun TodayHero(stats: PlayerStats, modifier: Modifier = Modifier) {
    val c = QuestLogTheme.colors
    val reduce = reducedMotion()

    val targetMinutes = (stats.todaySavedMs / 60_000L).toInt()
    val animatedMinutes by animateIntAsState(
        targetValue = targetMinutes,
        animationSpec = tween(600),
        label = "reclaimedMinutes",
    )
    val shownMinutes = if (reduce) targetMinutes else animatedMinutes
    val reclaimed = formatReclaimed(shownMinutes * 60_000L)
    val stableReclaimed = formatReclaimed(stats.todaySavedMs)
    val ringDescription = buildString {
        append("Reclaimed today: ")
        stableReclaimed.hours?.let { append(it).append(" ") }
        append(stableReclaimed.minutes)
        if (stats.consecutiveDetoxDays > 0) {
            append(", ").append(stats.consecutiveDetoxDays).append(" day streak, ")
            append(formatMultiplier(stats.streakMultiplier)).append(" rewards")
        }
    }

    Column(modifier) {
        Text("Reclaimed today".uppercase(), style = QuestType.label, color = c.inkMuted)
        Spacer(Modifier.height(QuestSpacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                fraction = ringFraction(stats.todaySavedMs, DetoxBudget.DEFAULT_DAILY_BUDGET_MS),
                modifier = Modifier.clearAndSetSemantics { contentDescription = ringDescription },
            ) {
                if (stats.consecutiveDetoxDays == 0) {
                    Text("—", style = QuestType.serifNumeral, color = c.inkMuted)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${stats.consecutiveDetoxDays}d", style = QuestType.serifNumeral, color = c.earned)
                        Text(formatMultiplier(stats.streakMultiplier), style = QuestType.caption, color = c.earned)
                    }
                }
            }
            Spacer(Modifier.width(QuestSpacing.lg))
            Text(
                buildAnnotatedString {
                    reclaimed.hours?.let {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = c.earned)) { append(it) }
                        append(" ")
                    }
                    withStyle(SpanStyle(color = c.inkPrimary)) { append(reclaimed.minutes) }
                },
                style = QuestType.display,
            )
        }
    }
}

internal fun fakeStats(
    level: Int = 6, xp: Long = 1950L, xpToNext: Long = 2100L, gold: Long = 250L,
    gems: Long = 0L, streakDays: Int = 6, mult: Float = 2.0f, savedMs: Long = 90 * 60_000L,
    shieldReady: Boolean = true,
) = PlayerStats(level, xp, xpToNext, gold, gems, streakDays, mult, savedMs, shieldReady)

@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.ui.tooling.preview.Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HeroPreview() {
    QuestLogTheme {
        Column(
            Modifier
                .background(QuestLogTheme.colors.ground)
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            TodayHero(fakeStats())
            Spacer(Modifier.height(16.dp))
            LevelBar(fakeStats())
        }
    }
}
