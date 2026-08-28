package com.example.questlog.ui.components

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.questlog.theme.QuestEmerald
import com.example.questlog.theme.QuestGold
import com.example.questlog.theme.QuestSlateBorder
import com.example.questlog.theme.QuestSlateCard
import com.example.questlog.theme.QuestTextMuted
import com.example.questlog.theme.QuestTextPrimary
import com.example.questlog.theme.QuestTextSecondary
import com.questlog.domain.model.DailyQuest

@Composable
fun DailyQuestBanner(
    quests: List<DailyQuest>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(QuestSlateCard)
            .border(1.dp, QuestSlateBorder, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "📜 DAILY QUESTS",
                color = QuestGold,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
            )
            Text(
                text = "${quests.count { it.isCompleted }}/${quests.size} Done",
                color = QuestTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        quests.forEach { quest ->
            DailyQuestItem(quest = quest)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DailyQuestItem(quest: DailyQuest) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (quest.isCompleted) QuestEmerald.copy(alpha = 0.08f)
                else QuestSlateBorder.copy(alpha = 0.3f)
            )
            .border(
                1.dp,
                if (quest.isCompleted) QuestEmerald.copy(alpha = 0.4f) else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(QuestSlateBorder.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = quest.icon, fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = quest.title,
                color = QuestTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Text(
                text = quest.description,
                color = QuestTextMuted,
                fontSize = 12.sp,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "+${quest.xpReward} XP",
                color = QuestGold,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
            Text(
                text = "+${quest.goldReward} G",
                color = QuestEmerald,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
            )
        }
    }
}
