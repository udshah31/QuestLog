package com.example.questlog.ui.today

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.questlog.theme.QuestIcons
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestSpacing
import com.example.questlog.theme.QuestType
import com.example.questlog.ui.common.SectionHeader
import com.questlog.domain.model.DailyQuest

@Composable
fun QuestLedger(quests: List<DailyQuest>, modifier: Modifier = Modifier) {
    val done = quests.count { it.isCompleted }
    Column(modifier.fillMaxWidth()) {
        SectionHeader("Today's quests", "$done / ${quests.size}")
        Spacer(Modifier.height(QuestSpacing.md))
        Column(verticalArrangement = Arrangement.spacedBy(QuestSpacing.md)) {
            quests.forEachIndexed { i, q ->
                QuestLedgerRow(index = i + 1, quest = q)
            }
        }
    }
}

@Composable
private fun QuestLedgerRow(index: Int, quest: DailyQuest) {
    val c = QuestLogTheme.colors
    val boxColor by animateColorAsState(
        targetValue = if (quest.isCompleted) c.earned else Color.Transparent,
        animationSpec = tween(200),
        label = "questBox",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "%02d".format(index),
            style = QuestType.serifNumeral,
            color = c.inkMuted,
            modifier = Modifier.width(18.dp),
        )
        Spacer(Modifier.width(QuestSpacing.sm))
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(boxColor)
                .border(1.5.dp, if (quest.isCompleted) c.earned else c.rule, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (quest.isCompleted) {
                Icon(QuestIcons.Check, contentDescription = null, tint = c.ground, modifier = Modifier.size(11.dp))
            }
        }
        Spacer(Modifier.width(QuestSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(quest.title, style = QuestType.bodyLarge, color = c.inkSecondary)
            Text(quest.description, style = QuestType.bodySmall, color = c.inkMuted)
        }
        Spacer(Modifier.width(QuestSpacing.sm))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "+${quest.xpReward}",
                style = QuestType.bodySmall,
                color = if (quest.isCompleted) c.earned else c.inkSecondary,
                textAlign = TextAlign.End,
            )
            Text("+${quest.goldReward} g", style = QuestType.caption, color = c.currency)
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.ui.tooling.preview.Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LedgerPreview() {
    QuestLogTheme {
        Column(
            Modifier.background(QuestLogTheme.colors.ground).padding(16.dp),
        ) {
            QuestLedger(
                listOf(
                    DailyQuest("a", "Budget Guardian", "Distraction apps under 30 min", 250, 60, true, ""),
                    DailyQuest("b", "Master Builder", "Build two in one day", 300, 70, true, ""),
                    DailyQuest("c", "Dawn Discipline", "Nothing before 9am", 150, 30, false, ""),
                ),
            )
        }
    }
}
