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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.questlog.theme.QuestArcane
import com.example.questlog.theme.QuestArcaneLight
import com.example.questlog.theme.QuestGold
import com.example.questlog.theme.QuestSlateBorder
import com.example.questlog.theme.QuestSlateCard
import com.example.questlog.theme.QuestSlateDark
import com.example.questlog.theme.QuestTextMuted
import com.example.questlog.theme.QuestTextPrimary
import com.example.questlog.theme.QuestTextSecondary

@Composable
fun PaywallModal(
    onDismiss: () -> Unit,
    onUnlockPro: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .border(2.dp, Brush.linearGradient(listOf(QuestGold, QuestArcane)), RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = QuestSlateCard),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Crown Hero
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(QuestGold, QuestArcane))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "👑", fontSize = 32.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "QUESTLOG PRO",
                    color = QuestGold,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    letterSpacing = 1.sp,
                )

                Text(
                    text = "Architect of the High Realm",
                    color = QuestArcaneLight,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Perks List
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(QuestSlateDark.copy(alpha = 0.6f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PerkRow(icon = "🏰", title = "Crystal Castle", desc = "Exclusive Mythic building")
                    PerkRow(icon = "⛲", title = "Aurora Fountain", desc = "Legendary cosmetic centerpiece")
                    PerkRow(icon = "⚡", title = "2x XP Multiplier", desc = "Double all focus time rewards")
                    PerkRow(icon = "🛡️", title = "Streak Freeze Shield", desc = "Protect your streak if you miss a day")
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Unlock Button
                Button(
                    onClick = onUnlockPro,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = QuestGold,
                        contentColor = QuestSlateDark,
                    ),
                ) {
                    Text(
                        text = "Unlock Pro ($4.99 / mo)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = QuestTextSecondary),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(QuestSlateBorder, QuestSlateBorder)))
                ) {
                    Text(text = "Maybe Later", fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Local receipt validation via RevenueCat SDK. Offline accessible.",
                    color = QuestTextMuted,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PerkRow(icon: String, title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = icon, fontSize = 18.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                color = QuestTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
            Text(
                text = desc,
                color = QuestTextSecondary,
                fontSize = 11.sp,
            )
        }
    }
}
