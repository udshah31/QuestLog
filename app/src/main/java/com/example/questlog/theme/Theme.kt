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
    )
} else {
    lightColorScheme(
        primary = c.earned, onPrimary = c.ground,
        background = c.ground, onBackground = c.inkPrimary,
        surface = c.surface, onSurface = c.inkPrimary,
        surfaceVariant = c.surfaceRaised, onSurfaceVariant = c.inkSecondary,
        outline = c.rule, scrim = c.scrim,
    )
}

@Composable
fun QuestLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) questDarkColors else questLightColors
    CompositionLocalProvider(LocalQuestColors provides colors) {
        MaterialTheme(
            colorScheme = schemeFor(colors, darkTheme),
            typography = QuestTypography,
            shapes = QuestShapes,
            content = content,
        )
    }
}
