package com.example.questlog.ui.realm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import com.example.questlog.theme.QuestIcons
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestSpacing
import com.example.questlog.theme.QuestType
import com.example.questlog.ui.common.Hairline
import com.example.questlog.ui.common.QuestScaffold
import com.questlog.domain.model.CityTile

@Composable
fun RealmScreen(
    tiles: List<CityTile>,
    gold: Long,
    onBack: () -> Unit,
    onTileClick: (CityTile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = QuestLogTheme.colors
    val built = tiles.count { it.isOwned }
    QuestScaffold(
        modifier = modifier,
        header = {
            Row(
                Modifier.fillMaxWidth().height(48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(QuestIcons.Back, contentDescription = "Back", tint = c.inkPrimary)
                    }
                    Text("Your Realm", style = QuestType.screenTitle, color = c.inkPrimary)
                }
                Text("$gold g", style = QuestType.serifNumeral, color = c.currency)
            }
        },
    ) {
        Hairline()
        Spacer(Modifier.height(QuestSpacing.md))
        Text("$built of ${tiles.size} built".uppercase(), style = QuestType.label, color = c.inkMuted)
        Spacer(Modifier.height(QuestSpacing.md))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(QuestSpacing.md),
            verticalArrangement = Arrangement.spacedBy(QuestSpacing.md),
            contentPadding = PaddingValues(bottom = QuestSpacing.xxl),
        ) {
            items(tiles, key = { it.itemId }) { tile ->
                RealmTile(tile = tile, onClick = { onTileClick(tile) })
            }
        }
        Text(
            text = "Tap a lit tile to build it",
            style = QuestType.caption,
            color = c.inkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = QuestSpacing.md),
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.ui.tooling.preview.Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RealmPreview() {
    QuestLogTheme {
        RealmScreen(
            tiles = com.example.questlog.ui.today.fakeTiles(),
            gold = 250L,
            onBack = {},
            onTileClick = {},
        )
    }
}
