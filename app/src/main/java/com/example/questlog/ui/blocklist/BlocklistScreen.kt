package com.example.questlog.ui.blocklist

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
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

@Composable
fun BlocklistScreen(
    state: BlocklistUiState,
    onIntent: (BlocklistIntent) -> Unit,
    onBack: () -> Unit,
    onGrantAccess: () -> Unit,
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
                IconButton(onClick = onBack) {
                    Icon(QuestIcons.Back, contentDescription = "Back", tint = c.inkPrimary)
                }
                Text("Distractions", style = QuestType.screenTitle, color = c.inkPrimary)
            }
        },
    ) {
        Hairline()
        Spacer(Modifier.height(QuestSpacing.md))

        if (!state.permissionGranted) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(QuestShapes.medium)
                    .border(1.dp, c.rule, QuestShapes.medium)
                    .drawBehind { drawRect(color = c.locked, size = Size(2.dp.toPx(), size.height)) }
                    .padding(QuestSpacing.md),
            ) {
                Text("Usage access needed", style = QuestType.bodyLarge, color = c.inkPrimary)
                Text(
                    "QuestLog needs usage access to measure time in these apps.",
                    style = QuestType.bodySmall, color = c.inkMuted,
                )
                Spacer(Modifier.height(QuestSpacing.sm))
                TextButton(onClick = onGrantAccess) {
                    Text("Grant access".uppercase(), style = QuestType.caption, color = c.locked)
                }
            }
            Spacer(Modifier.height(QuestSpacing.md))
        }

        // Search
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(c.surface)
                .padding(horizontal = QuestSpacing.md, vertical = QuestSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = state.query,
                onValueChange = { onIntent(BlocklistIntent.SetQuery(it)) },
                singleLine = true,
                textStyle = TextStyle(color = c.inkPrimary, fontSize = QuestType.bodyLarge.fontSize),
                cursorBrush = SolidColor(c.earned),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (state.query.isEmpty()) {
                            Text("Search apps", style = QuestType.bodyLarge, color = c.inkMuted)
                        }
                        inner()
                    }
                },
            )
        }
        Spacer(Modifier.height(QuestSpacing.md))

        when {
            state.loading -> Box(
                Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = c.earned, strokeWidth = 2.dp) }

            else -> LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(QuestSpacing.xs),
            ) {
                items(state.rows, key = { it.packageName }) { row ->
                    BlocklistRow(
                        row = row,
                        onToggle = { onIntent(BlocklistIntent.ToggleBlocked(row.packageName)) },
                        onSetLimit = { onIntent(BlocklistIntent.SetLimit(row.packageName, it)) },
                    )
                }
            }
        }

        Text(
            "Time in these apps beyond their limit is subtracted from your reclaimed time.",
            style = QuestType.caption, color = c.inkMuted, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = QuestSpacing.md),
        )
    }
}

private fun previewState(permission: Boolean) = BlocklistUiState(
    loading = false,
    permissionGranted = permission,
    rows = listOf(
        AppRow("com.instagram.android", "Instagram", null, blocked = true, dailyLimitMs = 30 * 60_000L),
        AppRow("com.spotify.music", "Spotify", null, blocked = false, dailyLimitMs = 0L),
        AppRow("com.reddit.frontpage", "Reddit", null, blocked = true, dailyLimitMs = 0L),
    ),
)

@Preview(name = "Blocklist")
@Preview(name = "Blocklist dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BlocklistScreenPreview() {
    QuestLogTheme {
        BlocklistScreen(previewState(permission = true), onIntent = {}, onBack = {}, onGrantAccess = {})
    }
}

@Preview(name = "Blocklist no permission")
@Composable
private fun BlocklistScreenNoPermissionPreview() {
    QuestLogTheme {
        BlocklistScreen(previewState(permission = false), onIntent = {}, onBack = {}, onGrantAccess = {})
    }
}
