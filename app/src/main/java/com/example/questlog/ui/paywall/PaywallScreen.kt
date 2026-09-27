package com.example.questlog.ui.paywall

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
import com.example.questlog.ui.dashboard.PaywallReason

private data class Perk(val mark: String, val title: String, val desc: String)

private val PRO_PERKS = listOf(
    Perk("×2", "Double rewards", "Every minute of focus time pays twice the XP and gold"),
    Perk("◇", "Streak Freeze", "Protects your streak through one missed day a week"),
    Perk("▢", "Two realm buildings", "Crystal Castle and Aurora Fountain"),
    Perk("✦", "Mindful Unlocks", "Say why, get 5 minutes free — as often as you like"),
)

internal enum class PaywallAction { Buy, Demo, None }
internal data class PaywallButton(val label: String, val action: PaywallAction)

internal fun paywallButton(
    priceText: String?,
    trialText: String?,
    offerLoading: Boolean,
    demoAvailable: Boolean,
): PaywallButton = when {
    priceText != null && trialText != null -> PaywallButton("Start free trial", PaywallAction.Buy)
    priceText != null -> PaywallButton("Unlock — $priceText", PaywallAction.Buy)
    offerLoading -> PaywallButton("Loading price…", PaywallAction.None)
    demoAvailable -> PaywallButton("Unlock — demo", PaywallAction.Demo)
    else -> PaywallButton("Pro unavailable right now", PaywallAction.None)
}

/** Sub-line under the headline. Any trial states its after-trial price (Play subscriptions policy). */
internal fun paywallSubline(reason: PaywallReason, priceText: String?, trialText: String?): String? = when {
    priceText == null -> null
    trialText != null && reason == PaywallReason.Milestone -> "Your realm earned a trial: $trialText, then $priceText."
    trialText != null -> "$trialText, then $priceText."
    reason == PaywallReason.Milestone -> "Keep the whole realm: $priceText."
    else -> null // manual, no trial: the price is on the button
}

@Composable
fun PaywallScreen(
    reason: PaywallReason,
    priceText: String?,
    trialText: String?,
    offerLoading: Boolean,
    purchasing: Boolean,
    demoAvailable: Boolean,
    onBuy: () -> Unit,
    onUnlockDemo: () -> Unit,
    onDismiss: () -> Unit,
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
                val milestone = reason == PaywallReason.Milestone
                Text(
                    (if (milestone) "7-day streak" else "Architect of the High Realm").uppercase(),
                    style = QuestType.label,
                    color = c.earned,
                )
                Text(
                    if (milestone) "Seven days kept." else "Keep the whole realm, not half of it.",
                    style = QuestType.heroLine,
                    color = c.inkPrimary,
                )
                paywallSubline(reason, priceText, trialText)?.let { subline ->
                    Text(
                        subline,
                        style = QuestType.bodyLarge,
                        color = c.inkSecondary,
                    )
                }
            }

            Column {
                Hairline()
                PRO_PERKS.forEach { perk ->
                    PerkRow(perk)
                    Hairline()
                }
            }

            val button = paywallButton(priceText, trialText, offerLoading, demoAvailable)
            Button(
                onClick = {
                    when (button.action) {
                        PaywallAction.Buy -> onBuy()
                        PaywallAction.Demo -> onUnlockDemo()
                        PaywallAction.None -> Unit
                    }
                },
                enabled = button.action != PaywallAction.None && !purchasing,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = QuestShapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.earned,
                    contentColor = c.ground,
                ),
            ) {
                Text(button.label, style = QuestType.bodyLarge)
            }

            if (trialText != null && priceText != null) {
                Text(
                    "Cancel anytime before the trial ends.",
                    style = QuestType.caption,
                    color = c.inkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Maybe later".uppercase(), style = QuestType.caption, color = c.inkMuted)
            }

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

@Preview(name = "Paywall — manual, loading")
@Composable
private fun PaywallScreenPreview() {
    QuestLogTheme {
        PaywallScreen(
            reason = PaywallReason.Manual, priceText = null, trialText = null,
            offerLoading = true, purchasing = false, demoAvailable = false,
            onBuy = {}, onUnlockDemo = {}, onDismiss = {},
        )
    }
}

@Preview(name = "Paywall — milestone, trial")
@Composable
private fun PaywallMilestonePreview() {
    QuestLogTheme {
        PaywallScreen(
            reason = PaywallReason.Milestone, priceText = "$4.99 / month", trialText = "7 days free",
            offerLoading = false, purchasing = false, demoAvailable = false,
            onBuy = {}, onUnlockDemo = {}, onDismiss = {},
        )
    }
}
