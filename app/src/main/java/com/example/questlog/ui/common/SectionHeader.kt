package com.example.questlog.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestType

/** An uppercase section label with a right-aligned serif count, e.g. "TODAY'S QUESTS   2 / 3". */
@Composable
fun SectionHeader(label: String, count: String, modifier: Modifier = Modifier) {
    val c = QuestLogTheme.colors
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(label.uppercase(), style = QuestType.label, color = c.inkMuted)
        Text(count, style = QuestType.serifNumeral, color = c.inkMuted)
    }
}
