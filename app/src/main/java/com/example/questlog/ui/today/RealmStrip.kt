package com.example.questlog.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.questlog.theme.QuestIcons
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestSpacing
import com.example.questlog.theme.QuestType
import com.example.questlog.ui.common.SectionHeader
import com.questlog.domain.model.CityTile

@Composable
fun RealmStrip(tiles: List<CityTile>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = QuestLogTheme.colors
    val built = tiles.count { it.isOwned }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics { contentDescription = "Your realm, $built of ${tiles.size} built. Open." }
            .padding(vertical = QuestSpacing.xs),
    ) {
        SectionHeader("Your realm", "$built / ${tiles.size}")
        Spacer(Modifier.height(QuestSpacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            tiles.forEach { tile ->
                val barMod = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                Box(
                    when {
                        tile.isOwned -> barMod.background(c.earned)
                        tile.isPremium -> barMod.border(1.dp, c.locked, RoundedCornerShape(3.dp))
                        else -> barMod.background(c.rule)
                    },
                )
                Spacer(Modifier.width(QuestSpacing.xs))
            }
            Spacer(Modifier.width(QuestSpacing.xs))
            Text("View".uppercase(), style = QuestType.caption, color = c.inkMuted)
            Spacer(Modifier.width(QuestSpacing.xs))
            Icon(QuestIcons.ArrowRight, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(14.dp))
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.ui.tooling.preview.Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RealmStripPreview() {
    QuestLogTheme {
        Column(Modifier.background(QuestLogTheme.colors.ground).padding(16.dp)) {
            RealmStrip(fakeTiles(), onOpen = {})
        }
    }
}

internal fun fakeTiles() = listOf(
    CityTile("town_hall", "Town Hall", 1, false, true, 0),
    CityTile("market", "Market", 1, false, true, 50),
    CityTile("library", "Library", 2, false, true, 120),
    CityTile("garden", "Zen Garden", 2, false, false, 200),
    CityTile("castle", "Crystal Castle", 3, true, false, 0),
    CityTile("fountain", "Aurora Fountain", 3, true, false, 0),
)
