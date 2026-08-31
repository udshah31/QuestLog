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

/**
 * Palette #1 — charcoal ink on paper-white, a single red accent. This is the app's only
 * colour scheme for now. Gold and Pro/locked have no dedicated hue in the palette: gold
 * falls back to slate grey, Pro/locked reuses the red accent.
 */
val questLightColors = QuestColors(
    groundTop = Color(0xFFFDF6F6),      // paper with the faintest warm cast at the gradient top
    ground = Color(0xFFFAF7FF),         // palette · paper
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFEDEBF2),
    rule = Color(0x26303841),           // #303841 @ 15%
    inkPrimary = Color(0xFF303841),     // palette
    inkSecondary = Color(0xFF48545C),   // palette
    inkMuted = Color(0xFF5E6A72),       // derived — clears WCAG AA on paper and white
    earned = Color(0xFFD72323),         // palette · the one accent
    currency = Color(0xFF48545C),       // no gold in the palette → slate grey
    locked = Color(0xFFD72323),         // Pro / locked markers reuse the red accent
    scrim = Color(0x59303841),          // #303841 @ 35% — modal scrim over paper
)

/** Dark mode is not yet designed for Palette #1; it falls back to the same scheme. */
val questDarkColors = questLightColors

val LocalQuestColors = staticCompositionLocalOf { questDarkColors }

/** Accessor for the semantic tokens: `QuestLogTheme.colors.earned`. */
object QuestLogTheme {
    val colors: QuestColors
        @Composable
        @ReadOnlyComposable
        get() = LocalQuestColors.current
}
