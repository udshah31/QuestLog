package com.example.questlog.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private fun schemeFor(c: QuestColors, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = c.earned, onPrimary = c.ground,
        background = c.ground, onBackground = c.inkPrimary,
        surface = c.surface, onSurface = c.inkPrimary,
        surfaceVariant = c.surfaceRaised, onSurfaceVariant = c.inkSecondary,
        outline = c.rule, scrim = c.scrim,
        inverseSurface = c.inkPrimary,
        inverseOnSurface = c.ground,
    )
} else {
    lightColorScheme(
        primary = c.earned, onPrimary = c.ground,
        background = c.ground, onBackground = c.inkPrimary,
        surface = c.surface, onSurface = c.inkPrimary,
        surfaceVariant = c.surfaceRaised, onSurfaceVariant = c.inkSecondary,
        outline = c.rule, scrim = c.scrim,
        inverseSurface = c.inkPrimary,
        inverseOnSurface = c.ground,
    )
}

@Composable
fun QuestLogTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Palette #1 is the app's only scheme for now; dark mode is not yet designed. The
    // darkTheme parameter and schemeFor's dark branch are kept so a real dark theme can
    // be reintroduced without reworking the plumbing.
    val colors = questLightColors
    CompositionLocalProvider(LocalQuestColors provides colors) {
        MaterialTheme(
            colorScheme = schemeFor(colors, dark = false),
            typography = QuestTypography,
            shapes = QuestShapes,
            content = content,
        )
    }
}
