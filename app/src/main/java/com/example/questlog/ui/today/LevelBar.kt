package com.example.questlog.ui.today

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestSpacing
import com.example.questlog.theme.QuestType
import com.example.questlog.ui.format.levelTitle
import com.questlog.domain.model.PlayerStats
import com.questlog.util.TimeConversion

@Composable
fun LevelBar(stats: PlayerStats, modifier: Modifier = Modifier) {
    val c = QuestLogTheme.colors
    val progress by animateFloatAsState(
        targetValue = TimeConversion.xpProgress(stats.xp),
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "xp",
    )
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Level ${stats.level} · ${levelTitle(stats.level)}", style = QuestType.bodySmall, color = c.inkMuted)
            Text("${(progress * 100).toInt()}%", style = QuestType.serifNumeral, color = c.inkMuted)
        }
        Spacer(Modifier.height(QuestSpacing.sm))
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(c.rule),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(3.dp)
                    .background(c.inkSecondary),
            )
        }
    }
}
