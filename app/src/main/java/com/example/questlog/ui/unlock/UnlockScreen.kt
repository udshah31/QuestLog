package com.example.questlog.ui.unlock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.questlog.theme.QuestIcons
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestShapes
import com.example.questlog.theme.QuestSpacing
import com.example.questlog.theme.QuestType
import com.example.questlog.ui.common.Hairline
import com.example.questlog.ui.common.QuestScaffold

@Composable
fun UnlockScreen(state: UnlockUiState, onIntent: (UnlockIntent) -> Unit, onBack: () -> Unit) {
    val c = QuestLogTheme.colors
    val keyboard = LocalSoftwareKeyboardController.current
    QuestScaffold(
        header = {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(QuestIcons.Back, contentDescription = "Back", tint = c.inkPrimary) }
                Text("Open mindfully", style = QuestType.screenTitle, color = c.inkPrimary)
            }
        },
    ) {
        Hairline()
        val app = state.selected
        if (app == null || state.phase == UnlockPhase.Pick) {
            if (state.loaded && state.apps.isEmpty()) {
                Text(
                    "Add apps under Distractions first.",
                    style = QuestType.bodyLarge,
                    color = c.inkMuted,
                    modifier = Modifier.padding(top = QuestSpacing.lg),
                )
            }
            LazyColumn {
                items(state.apps, key = { it.packageName }) { a ->
                    Text(
                        a.label,
                        style = QuestType.bodyLarge,
                        color = c.inkPrimary,
                        modifier = Modifier.fillMaxWidth()
                            .clickable(role = Role.Button) { onIntent(UnlockIntent.Select(a)) }
                            .padding(vertical = QuestSpacing.md),
                    )
                    Hairline()
                }
            }
            return@QuestScaffold
        }
        Column(Modifier.padding(top = QuestSpacing.lg), verticalArrangement = Arrangement.spacedBy(QuestSpacing.md)) {
            when (state.phase) {
                UnlockPhase.Reason, UnlockPhase.Checking -> {
                    Text("What are you opening ${app.label} for?", style = QuestType.heroLine, color = c.inkPrimary)
                    OutlinedTextField(
                        value = state.reason,
                        onValueChange = { onIntent(UnlockIntent.SetReason(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Reason") },
                        enabled = state.phase == UnlockPhase.Reason,
                    )
                    PrimaryButton(
                        if (state.phase == UnlockPhase.Checking) "Checking…" else "Check",
                        enabled = state.phase == UnlockPhase.Reason && state.reason.isNotBlank(),
                    ) {
                        keyboard?.hide() // else it stays up over the verdict or the paywall
                        onIntent(UnlockIntent.Check)
                    }
                    if (state.loaded) {
                        Caption(if (!state.isPremium && state.freeLeft > 0) "1 free mindful unlock left today" else "Unlimited with Pro")
                    }
                    Caption("Checked by AI, not saved by QuestLog.")
                }
                UnlockPhase.Granted -> {
                    Text("5 minutes, no charge.", style = QuestType.heroLine, color = c.inkPrimary)
                    PrimaryButton("Open ${app.label}") { onIntent(UnlockIntent.Open) }
                }
                UnlockPhase.Drifting -> {
                    Text("That sounds like drifting — it'll count today.", style = QuestType.heroLine, color = c.inkPrimary)
                    PrimaryButton("Open anyway") { onIntent(UnlockIntent.Open) }
                    TextButton(onClick = { onIntent(UnlockIntent.NotNow) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Not now".uppercase(), style = QuestType.caption, color = c.inkMuted)
                    }
                }
                UnlockPhase.Unavailable -> {
                    Text("Couldn't check right now.", style = QuestType.heroLine, color = c.inkPrimary)
                    PrimaryButton("Open anyway") { onIntent(UnlockIntent.Open) }
                }
                UnlockPhase.Pick -> Unit
            }
        }
    }
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = QuestLogTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = QuestShapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = c.earned, contentColor = c.ground),
    ) { Text(label, style = QuestType.bodyLarge) }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = QuestType.caption, color = QuestLogTheme.colors.inkMuted)
}
