package com.example.questlog.ui.realm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestSpacing
import com.example.questlog.theme.QuestType
import com.questlog.domain.model.CityTile

@Composable
fun RealmTile(tile: CityTile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = QuestLogTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val tier = when (tile.tier) { 1 -> "I"; 2 -> "II"; else -> "III" }
    val (statusText, statusColor) = when {
        tile.isOwned -> "Built" to c.earned
        tile.isPremium -> "Pro" to c.locked
        tile.goldCost == 0L -> "Free" to c.currency
        else -> "${tile.goldCost} g" to c.currency
    }
    Column(
        modifier
            .clip(shape)
            .background(c.surface)
            .then(
                if (tile.isPremium && !tile.isOwned) {
                    Modifier.drawBehind {
                        drawRoundRect(
                            color = c.locked,
                            style = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                            ),
                            cornerRadius = CornerRadius(14.dp.toPx()),
                        )
                    }
                } else {
                    Modifier.border(1.dp, if (tile.isOwned) c.earned.copy(alpha = 0.3f) else c.rule, shape)
                },
            )
            .clickable(onClick = onClick)
            .padding(QuestSpacing.md)
            .fillMaxWidth()
            .height(84.dp),
    ) {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
            Text(tile.displayName, style = QuestType.bodyLarge, color = c.inkPrimary, modifier = Modifier.weight(1f))
            Text(tier, style = QuestType.serifNumeral, color = c.inkMuted)
        }
        Spacer(Modifier.weight(1f))
        Text(statusText.uppercase(), style = QuestType.caption, color = statusColor)
    }
}

@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.ui.tooling.preview.Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RealmTilePreview() {
    QuestLogTheme {
        androidx.compose.foundation.layout.Row(
            Modifier
                .background(QuestLogTheme.colors.ground)
                .padding(QuestSpacing.md),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(QuestSpacing.md),
        ) {
            RealmTile(com.example.questlog.ui.today.fakeTiles()[0], onClick = {}, modifier = Modifier.weight(1f))
            RealmTile(com.example.questlog.ui.today.fakeTiles()[4], onClick = {}, modifier = Modifier.weight(1f))
        }
    }
}
