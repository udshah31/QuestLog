package com.example.questlog.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.questlog.R

/** Bundled display face (OFL-1.1). Regular + Italic only — never bold. */
val InstrumentSerif = FontFamily(
    Font(R.font.instrument_serif_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

@Preview
@Composable
private fun SerifPreview() {
    QuestLogTheme {
        Column(
            Modifier
                .background(QuestLogTheme.colors.ground)
                .padding(20.dp),
        ) {
            Text("questlog", style = QuestType.wordmark, color = QuestLogTheme.colors.inkPrimary)
            Text("1h 30m", style = QuestType.display, color = QuestLogTheme.colors.inkPrimary)
            Text("2h", style = QuestType.displayItalic, color = QuestLogTheme.colors.earned)
        }
    }
}
