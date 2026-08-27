package com.example.questlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.questlog.theme.QuestArcane
import com.example.questlog.theme.QuestEmerald
import com.example.questlog.theme.QuestGold
import com.example.questlog.theme.QuestSlateBorder
import com.example.questlog.theme.QuestSlateCard
import com.example.questlog.theme.QuestTextMuted
import com.example.questlog.theme.QuestTextPrimary
import com.example.questlog.theme.QuestTextSecondary
import com.questlog.domain.model.CityTile

@Composable
fun CityGrid(
    tiles: List<CityTile>,
    onTileClick: (CityTile) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(QuestSlateCard)
            .border(1.dp, QuestSlateBorder, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "🏰 SANCTUARY REALM",
                    color = QuestGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp,
                )
            }
            Text(
                text = "${tiles.count { it.isOwned }}/${tiles.size} Built",
                color = QuestTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2-column Grid of buildings
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val chunked = tiles.chunked(2)
            chunked.forEach { rowTiles ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowTiles.forEach { tile ->
                        CityTileItem(
                            tile = tile,
                            onClick = { onTileClick(tile) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowTiles.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CityTileItem(
    tile: CityTile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = when (tile.itemId) {
        "town_hall" -> "🏛️"
        "market" -> "🛒"
        "library" -> "📚"
        "garden" -> "🌿"
        "castle" -> "🏰"
        "fountain" -> "⛲"
        else -> "🏠"
    }

    val isLocked = !tile.isOwned

    val backgroundBrush = if (tile.isOwned) {
        if (tile.isPremium) Brush.linearGradient(listOf(QuestArcane.copy(alpha = 0.25f), QuestSlateCard))
        else Brush.linearGradient(listOf(QuestSlateBorder.copy(alpha = 0.5f), QuestSlateBorder.copy(alpha = 0.5f)))
    } else {
        Brush.linearGradient(listOf(QuestSlateBorder.copy(alpha = 0.2f), QuestSlateBorder.copy(alpha = 0.2f)))
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundBrush)
            .border(
                1.dp,
                when {
                    tile.isOwned && tile.isPremium -> QuestArcane
                    tile.isOwned -> QuestEmerald.copy(alpha = 0.6f)
                    tile.isPremium -> QuestArcane.copy(alpha = 0.4f)
                    else -> QuestSlateBorder
                },
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Icon + Status
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        if (tile.isOwned) QuestSlateCard else QuestSlateBorder.copy(alpha = 0.5f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = icon,
                    fontSize = 24.sp,
                    modifier = Modifier.padding(2.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = tile.displayName,
                color = if (tile.isOwned) QuestTextPrimary else QuestTextSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Cost or Owned Status Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            tile.isOwned -> QuestEmerald.copy(alpha = 0.2f)
                            tile.isPremium -> QuestArcane.copy(alpha = 0.2f)
                            else -> QuestGold.copy(alpha = 0.15f)
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = when {
                        tile.isOwned -> "✓ BUILT"
                        tile.isPremium -> "👑 PRO"
                        tile.goldCost == 0L -> "FREE"
                        else -> "${tile.goldCost} G"
                    },
                    color = when {
                        tile.isOwned -> QuestEmerald
                        tile.isPremium -> QuestArcane
                        else -> QuestGold
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
