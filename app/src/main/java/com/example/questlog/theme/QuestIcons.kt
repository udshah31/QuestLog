package com.example.questlog.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** Minimal 24dp line glyphs. Tint at the call site via Icon(tint = …). */
object QuestIcons {

    private fun line(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    val Back: ImageVector = line("Back") { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) }

    val ArrowRight: ImageVector = line("ArrowRight") {
        moveTo(5f, 12f); lineTo(19f, 12f); moveTo(13f, 6f); lineTo(19f, 12f); lineTo(13f, 18f)
    }

    val Check: ImageVector = line("Check") { moveTo(5f, 13f); lineTo(10f, 18f); lineTo(19f, 6f) }

    val Refresh: ImageVector = line("Refresh") {
        moveTo(20f, 11f)
        arcTo(8f, 8f, 0f, true, false, 20.5f, 14f)
        moveTo(20f, 5f); lineTo(20f, 11f); lineTo(14f, 11f)
    }

    val Crown: ImageVector = line("Crown") {
        moveTo(4f, 18f); lineTo(20f, 18f)
        moveTo(4f, 18f); lineTo(4f, 8f); lineTo(9f, 12f); lineTo(12f, 6f); lineTo(15f, 12f); lineTo(20f, 8f); lineTo(20f, 18f)
    }

    val Lock: ImageVector = line("Lock") {
        moveTo(6f, 11f); lineTo(18f, 11f); lineTo(18f, 20f); lineTo(6f, 20f); close()
        moveTo(8f, 11f); lineTo(8f, 8f)
        arcTo(4f, 4f, 0f, true, true, 16f, 8f)
        lineTo(16f, 11f)
    }

    val Settings: ImageVector = line("Settings") {
        // hexagon-ish gear: outer ring + center dot
        moveTo(12f, 4f); lineTo(19f, 8f); lineTo(19f, 16f); lineTo(12f, 20f); lineTo(5f, 16f); lineTo(5f, 8f); close()
        moveTo(12f, 9f)
        arcTo(3f, 3f, 0f, true, true, 11.99f, 9f)
    }
}

@Preview
@Composable
private fun IconsPreview() {
    QuestLogTheme {
        Row(
            modifier = androidx.compose.ui.Modifier
                .background(QuestLogTheme.colors.ground)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf(QuestIcons.Back, QuestIcons.ArrowRight, QuestIcons.Check, QuestIcons.Refresh, QuestIcons.Crown, QuestIcons.Lock, QuestIcons.Settings).forEach {
                Icon(it, contentDescription = null, tint = QuestLogTheme.colors.inkPrimary)
            }
        }
    }
}
