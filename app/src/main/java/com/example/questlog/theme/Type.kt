package com.example.questlog.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val Serif = InstrumentSerif
private val Body = FontFamily.Default

object QuestType {
    val display = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-0.01).em)
    val displayItalic = display.copy(fontStyle = FontStyle.Italic)
    val wordmark = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 26.sp)
    val screenTitle = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 24.sp)
    val serifNumeral = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 18.sp)
    val bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
    val bodySmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp)
    val label = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.2.em)
    val caption = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 9.sp, lineHeight = 12.sp, letterSpacing = 0.12.em)
}

val QuestTypography = Typography(
    displaySmall = QuestType.display,
    titleLarge = QuestType.screenTitle,
    labelLarge = QuestType.serifNumeral,
    bodyLarge = QuestType.bodyLarge,
    bodySmall = QuestType.bodySmall,
    labelMedium = QuestType.label,
    labelSmall = QuestType.caption,
)
