package com.example.questlog.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RpgColorScheme = darkColorScheme(
    primary = QuestGold,
    onPrimary = QuestSlateDark,
    primaryContainer = QuestGoldDark,
    onPrimaryContainer = QuestGoldLight,
    secondary = QuestEmerald,
    onSecondary = QuestSlateDark,
    secondaryContainer = QuestEmerald,
    onSecondaryContainer = QuestEmeraldLight,
    tertiary = QuestArcane,
    onTertiary = QuestSlateDark,
    background = QuestSlateDark,
    onBackground = QuestTextPrimary,
    surface = QuestSlateCard,
    onSurface = QuestTextPrimary,
    surfaceVariant = QuestSlateBorder,
    onSurfaceVariant = QuestTextSecondary,
)

@Composable
fun QuestLogTheme(
    darkTheme: Boolean = true, // RPG theme defaults to immersive dark mode
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = RpgColorScheme,
        typography = Typography,
        content = content,
    )
}
