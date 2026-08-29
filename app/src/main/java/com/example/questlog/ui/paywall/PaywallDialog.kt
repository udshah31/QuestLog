package com.example.questlog.ui.paywall

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.questlog.theme.QuestIcons
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestShapes
import com.example.questlog.theme.QuestSpacing
import com.example.questlog.theme.QuestType
import com.example.questlog.ui.common.Hairline

@Composable
fun PaywallDialog(onDismiss: () -> Unit, onUnlockPro: () -> Unit) {
    val c = QuestLogTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(QuestShapes.large)
                .background(c.surface)
                .padding(QuestSpacing.xl)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(QuestSpacing.md),
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(42.dp).clip(CircleShape).border(1.dp, c.locked, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(QuestIcons.Crown, contentDescription = null, tint = c.locked, modifier = Modifier.size(20.dp))
            }
            Text("Questlog Pro", style = QuestType.screenTitle, color = c.inkPrimary)
            Text("Architect of the High Realm".uppercase(), style = QuestType.caption, color = c.locked)
            Hairline()
            PerkRow(mark = "×2", title = "Double rewards", desc = "Every minute of focus time pays twice the XP and gold")
            PerkRow(mark = "◇", title = "Streak Freeze", desc = "Protects your streak through one missed day a week")
            PerkRow(mark = "▢", title = "Two realm buildings", desc = "Crystal Castle and Aurora Fountain")
            Spacer(Modifier.height(QuestSpacing.xs))
            Button(
                onClick = onUnlockPro,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = QuestShapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = c.earned, contentColor = c.ground),
            ) {
                Text("Unlock — \$4.99 / month", style = QuestType.bodyLarge)
            }
            TextButton(onClick = onDismiss) {
                Text("Maybe later".uppercase(), style = QuestType.caption, color = c.inkMuted)
            }
            Text(
                "Local receipt validation via RevenueCat SDK. Offline accessible.",
                style = QuestType.caption, color = c.inkMuted, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PerkRow(mark: String, title: String, desc: String) {
    val c = QuestLogTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(mark, style = QuestType.serifNumeral, color = c.earned, modifier = Modifier.width(24.dp))
        Spacer(Modifier.width(QuestSpacing.sm))
        Column {
            Text(title, style = QuestType.bodyLarge, color = c.inkPrimary)
            Text(desc, style = QuestType.bodySmall, color = c.inkMuted)
        }
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PaywallPreview() {
    QuestLogTheme { PaywallDialog(onDismiss = {}, onUnlockPro = {}) }
}
