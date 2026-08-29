package com.example.questlog.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Semantic colour roles. Identical names across themes; values differ. */
data class QuestColors(
    val groundTop: Color,
    val ground: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val rule: Color,
    val inkPrimary: Color,
    val inkSecondary: Color,
    val inkMuted: Color,
    val earned: Color,
    val currency: Color,
    val locked: Color,
    val scrim: Color,
)

val questDarkColors = QuestColors(
    groundTop = Color(0xFF141B2E),
    ground = Color(0xFF0B0E17),
    surface = Color(0xFF161C2B),
    surfaceRaised = Color(0xFF1E2536),
    rule = Color(0x29969EC0),           // rgba(150,158,192,0.16)
    inkPrimary = Color(0xFFE5E8F2),
    inkSecondary = Color(0xFFA9B0C7),
    inkMuted = Color(0xFF828BA8),
    earned = Color(0xFF6EE7D4),
    currency = Color(0xFFE0A458),
    locked = Color(0xFFA78BE6),
    scrim = Color(0x9E06080E),          // rgba(6,8,14,0.62)
)

val questLightColors = QuestColors(
    groundTop = Color(0xFFFBFAF6),
    ground = Color(0xFFF1F2F7),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFECEDF3),
    rule = Color(0x337880A0),           // rgba(120,128,160,0.20)
    inkPrimary = Color(0xFF1F2333),
    inkSecondary = Color(0xFF4B5168),
    inkMuted = Color(0xFF676D85),
    earned = Color(0xFF0A7060),
    currency = Color(0xFF8C5C11),
    locked = Color(0xFF6B4FC7),
    scrim = Color(0x4D141628),          // rgba(20,22,40,0.30)
)

val LocalQuestColors = staticCompositionLocalOf { questDarkColors }

/** Accessor for the semantic tokens: `QuestLogTheme.colors.earned`. */
object QuestLogTheme {
    val colors: QuestColors
        @Composable
        @ReadOnlyComposable
        get() = LocalQuestColors.current
}
