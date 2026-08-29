package com.example.questlog.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.theme.QuestType

/** Small rounded status/action chip. `filled` = earned fill; else a locked outline. */
@Composable
fun Pill(
    text: String,
    filled: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val c = QuestLogTheme.colors
    val shape = RoundedCornerShape(999.dp)
    var m = modifier.clip(shape)
    m = if (filled) m.background(c.earned) else m.border(BorderStroke(1.dp, c.locked), shape)
    if (onClick != null) m = m.clickable(role = Role.Button, onClick = onClick)
    m = m.padding(horizontal = 10.dp, vertical = 5.dp)
    Text(
        text = text.uppercase(),
        style = QuestType.caption,
        color = if (filled) c.ground else c.locked,
        modifier = m,
    )
}
