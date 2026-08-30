package com.example.questlog.ui.blocklist

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.questlog.theme.QuestIcons
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestSpacing
import com.example.questlog.theme.QuestType
import com.example.questlog.ui.common.Pill

private val LIMIT_PRESETS_MS = listOf(0L, 15 * 60_000L, 30 * 60_000L, 60 * 60_000L)

private fun limitLabel(ms: Long): String = when (ms) {
    0L -> "Off"
    else -> {
        val m = ms / 60_000L
        if (m % 60 == 0L) "${m / 60}h" else "${m}m"
    }
}

@Composable
fun BlocklistRow(
    row: AppRow,
    onToggle: () -> Unit,
    onSetLimit: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = QuestLogTheme.colors
    Column(modifier.fillMaxWidth().padding(vertical = QuestSpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val bmp = row.icon?.let { runCatching { it.toBitmap() }.getOrNull() }
            if (bmp != null) {
                Image(
                    painter = BitmapPainter(bmp.asImageBitmap()),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)),
                )
            } else {
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(c.surfaceRaised),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        QuestIcons.Lock,
                        contentDescription = null,
                        tint = c.inkMuted,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Spacer(Modifier.size(QuestSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(row.label, style = QuestType.bodyLarge, color = c.inkPrimary)
                Text(row.packageName, style = QuestType.caption, color = c.inkMuted)
            }
            Switch(
                checked = row.blocked,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = c.ground,
                    checkedTrackColor = c.earned,
                    uncheckedThumbColor = c.inkMuted,
                    uncheckedTrackColor = c.surfaceRaised,
                    uncheckedBorderColor = c.rule,
                ),
            )
        }
        AnimatedVisibility(visible = row.blocked) {
            Column {
                Spacer(Modifier.height(QuestSpacing.sm))
                Text("DAILY LIMIT", style = QuestType.label, color = c.inkMuted)
                Spacer(Modifier.height(QuestSpacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(QuestSpacing.sm)) {
                    LIMIT_PRESETS_MS.forEach { ms ->
                        Pill(
                            text = limitLabel(ms),
                            filled = row.dailyLimitMs == ms,
                            onClick = { onSetLimit(ms) },
                        )
                    }
                }
            }
        }
    }
}

private fun previewRow(blocked: Boolean, limit: Long = 0L) = AppRow(
    packageName = "com.instagram.android",
    label = "Instagram",
    icon = null,
    blocked = blocked,
    dailyLimitMs = limit,
)

@Preview
@Composable
private fun BlocklistRowPreview() {
    QuestLogTheme {
        Column(Modifier.background(QuestLogTheme.colors.ground).padding(16.dp)) {
            BlocklistRow(previewRow(blocked = false), onToggle = {}, onSetLimit = {})
            BlocklistRow(previewRow(blocked = true, limit = 30 * 60_000L), onToggle = {}, onSetLimit = {})
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BlocklistRowPreviewDark() {
    QuestLogTheme {
        Column(Modifier.background(QuestLogTheme.colors.ground).padding(16.dp)) {
            BlocklistRow(previewRow(blocked = false), onToggle = {}, onSetLimit = {})
            BlocklistRow(previewRow(blocked = true, limit = 30 * 60_000L), onToggle = {}, onSetLimit = {})
        }
    }
}
