package com.example.questlog.theme

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable

@Preview(name = "Tokens light")
@Preview(name = "Tokens dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TokenSwatches() {
    QuestLogTheme {
        val c = QuestLogTheme.colors
        Column(
            Modifier
                .background(c.ground)
                .padding(16.dp),
        ) {
            listOf(
                "ground" to c.ground, "surface" to c.surface, "rule" to c.rule,
                "inkPrimary" to c.inkPrimary, "inkSecondary" to c.inkSecondary, "inkMuted" to c.inkMuted,
                "earned" to c.earned, "currency" to c.currency, "locked" to c.locked,
            ).forEach { (name, col) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 3.dp),
                ) {
                    Box(
                        Modifier.size(22.dp).background(col),
                    )
                    Text(
                        "  $name", style = QuestType.bodySmall, color = c.inkPrimary,
                    )
                }
            }
        }
    }
}
