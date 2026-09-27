package com.example.questlog.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.questlog.theme.QuestIcons
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestSpacing
import com.example.questlog.theme.QuestType

@Composable
fun MindfulUnlockRow(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = QuestLogTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onOpen)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .padding(vertical = QuestSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Open an app mindfully", style = QuestType.bodyLarge, color = c.inkPrimary, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(QuestSpacing.xs))
        Icon(QuestIcons.ArrowRight, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(14.dp))
    }
}
