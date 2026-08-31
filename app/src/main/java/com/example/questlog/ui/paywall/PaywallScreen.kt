package com.example.questlog.ui.paywall

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.questlog.theme.QuestIcons
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestShapes
import com.example.questlog.theme.QuestSpacing
import com.example.questlog.theme.QuestType
import com.example.questlog.ui.common.Hairline
import com.example.questlog.ui.common.QuestScaffold

private data class Perk(val mark: String, val title: String, val desc: String)

private val PRO_PERKS = listOf(
    Perk("×2", "Double rewards", "Every minute of focus time pays twice the XP and gold"),
    Perk("◇", "Streak Freeze", "Protects your streak through one missed day a week"),
    Perk("▢", "Two realm buildings", "Crystal Castle and Aurora Fountain"),
)

@Composable
fun PaywallScreen(
    onDismiss: () -> Unit,
    onUnlockPro: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = QuestLogTheme.colors
    QuestScaffold(
        modifier = modifier,
        header = {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(QuestIcons.Back, contentDescription = "Back", tint = c.inkPrimary)
                }
                Text("QuestLog Pro", style = QuestType.screenTitle, color = c.inkPrimary)
            }
        },
    ) {
        Hairline()
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(QuestSpacing.lg),
        ) {
            Spacer(Modifier.height(QuestSpacing.md))

            Column(verticalArrangement = Arrangement.spacedBy(QuestSpacing.sm)) {
                Text(
                    "Architect of the High Realm".uppercase(),
                    style = QuestType.label,
                    color = c.earned,
                )
                Text(
                    "Keep the whole realm, not half of it.",
                    style = QuestType.heroLine,
                    color = c.inkPrimary,
                )
            }

            Column {
                Hairline()
                PRO_PERKS.forEach { perk ->
                    PerkRow(perk)
                    Hairline()
                }
            }

            Button(
                onClick = onUnlockPro,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = QuestShapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.earned,
                    contentColor = c.ground,
                ),
            ) {
                Text("Unlock — \$4.99 / month", style = QuestType.bodyLarge)
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Maybe later".uppercase(), style = QuestType.caption, color = c.inkMuted)
            }

            Text(
                "Local receipt validation via RevenueCat. Works offline.",
                style = QuestType.caption,
                color = c.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(QuestSpacing.xxl))
        }
    }
}

@Composable
private fun PerkRow(perk: Perk) {
    val c = QuestLogTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = QuestSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            perk.mark,
            style = QuestType.serifNumeral,
            color = c.earned,
            modifier = Modifier.width(28.dp),
        )
        Spacer(Modifier.width(QuestSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(perk.title, style = QuestType.bodyLarge, color = c.inkPrimary)
            Text(perk.desc, style = QuestType.bodySmall, color = c.inkMuted)
        }
    }
}

@Preview(name = "Paywall")
@Preview(name = "Paywall dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PaywallScreenPreview() {
    QuestLogTheme {
        PaywallScreen(onDismiss = {}, onUnlockPro = {})
    }
}
