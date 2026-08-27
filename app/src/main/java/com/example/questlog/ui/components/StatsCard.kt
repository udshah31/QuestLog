package com.example.questlog.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.questlog.theme.QuestAmber
import com.example.questlog.theme.QuestArcane
import com.example.questlog.theme.QuestArcaneLight
import com.example.questlog.theme.QuestCyan
import com.example.questlog.theme.QuestEmerald
import com.example.questlog.theme.QuestGold
import com.example.questlog.theme.QuestSlateBorder
import com.example.questlog.theme.QuestSlateCard
import com.example.questlog.theme.QuestTextMuted
import com.example.questlog.theme.QuestTextPrimary
import com.example.questlog.theme.QuestTextSecondary
import com.questlog.domain.model.PlayerStats

@Composable
fun StatsCard(
    stats: PlayerStats,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = com.questlog.util.TimeConversion.xpProgress(stats.xp),
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "xpProgress",
    )

    val levelTitle = when (stats.level) {
        1 -> "Novice of Will"
        2 -> "Seeker of Focus"
        3 -> "Guardian of Time"
        4 -> "Knight of Discipline"
        else -> "Grandmaster of Focus"
    }

    val savedMinutes = stats.todaySavedMs / 60_000L
    val savedHours = savedMinutes / 60
    val remMinutes = savedMinutes % 60
    val timeSavedFormatted = if (savedHours > 0) "${savedHours}h ${remMinutes}m" else "${remMinutes}m"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(QuestSlateCard)
            .border(1.dp, QuestSlateBorder, RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        // Top row: Level Badge & Title + Streak
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(QuestGold, QuestArcane))
                        )
                        .border(2.dp, QuestGold, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${stats.level}",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "LEVEL ${stats.level}",
                        color = QuestGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        text = levelTitle,
                        color = QuestTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                }
            }

            // Streak Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(QuestAmber.copy(alpha = 0.15f))
                    .border(1.dp, QuestAmber.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "🔥 ${stats.consecutiveDetoxDays}d (${String.format("%.1fx", stats.streakMultiplier)})",
                    color = QuestAmber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // XP Progress Bar
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "EXP: ${stats.xp} / ${stats.xpToNextLevel} XP",
                    color = QuestTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${(animatedProgress * 100).toInt()}%",
                    color = QuestCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = QuestCyan,
                trackColor = QuestSlateBorder,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Bottom Stats Grid: Time Saved, Gold, Gems
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatMetricItem(
                label = "Time Saved",
                value = timeSavedFormatted,
                valueColor = QuestEmerald,
                icon = "⏳",
            )
            StatMetricItem(
                label = "Gold Balance",
                value = "${stats.gold} G",
                valueColor = QuestGold,
                icon = "🪙",
            )
            StatMetricItem(
                label = "Gems",
                value = "${stats.gems}",
                valueColor = QuestArcaneLight,
                icon = "💎",
            )
        }
    }
}

@Composable
private fun StatMetricItem(
    label: String,
    value: String,
    valueColor: Color,
    icon: String,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(QuestSlateBorder.copy(alpha = 0.4f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = icon, fontSize = 13.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = QuestTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = valueColor,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
    }
}
