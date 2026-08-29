package com.example.questlog.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestSpacing

/**
 * Every screen's frame: the dusk radial-gradient ground, system-bar insets, a fixed
 * [header] slot, then scroll-free [content] laid out in a Column with lg horizontal padding.
 */
@Composable
fun QuestScaffold(
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = QuestLogTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(c.ground)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(c.groundTop, c.ground),
                        center = Offset(size.width / 2f, 0f),
                        radius = size.height * 0.9f,
                    ),
                    size = Size(size.width, size.height * 0.6f),
                )
            }
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = QuestSpacing.lg),
    ) {
        header()
        content()
    }
}

@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.ui.tooling.preview.Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PrimitivesPreview() {
    QuestLogTheme {
        QuestScaffold(header = {
            androidx.compose.material3.Text("Questlog", style = com.example.questlog.theme.QuestType.wordmark, color = QuestLogTheme.colors.inkPrimary)
        }) {
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(QuestSpacing.md))
            Hairline()
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(QuestSpacing.md))
            SectionHeader("Today's quests", "2 / 3")
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(QuestSpacing.md))
            androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(QuestSpacing.sm)) {
                Pill("Get Pro", filled = false, onClick = {})
                Pill("Pro", filled = true, onClick = null)
            }
        }
    }
}
