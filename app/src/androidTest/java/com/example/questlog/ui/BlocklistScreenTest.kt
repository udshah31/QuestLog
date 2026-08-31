package com.example.questlog.ui

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.ui.blocklist.AppRow
import com.example.questlog.ui.blocklist.BlocklistIntent
import com.example.questlog.ui.blocklist.BlocklistScreen
import com.example.questlog.ui.blocklist.BlocklistUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BlocklistScreenTest {

    @get:Rule val rule = createComposeRule()

    private fun state(permission: Boolean = true) = BlocklistUiState(
        loading = false,
        permissionGranted = permission,
        rows = listOf(
            AppRow("com.a", "Alpha", null, blocked = false, dailyLimitMs = 0L),
            AppRow("com.b", "Bravo", null, blocked = true, dailyLimitMs = 0L),
        ),
    )

    @Test
    fun toggling_a_row_emits_ToggleBlocked() {
        val intents = mutableListOf<BlocklistIntent>()
        rule.setContent {
            QuestLogTheme {
                BlocklistScreen(state(), onIntent = { intents.add(it) }, onBack = {}, onGrantAccess = {})
            }
        }
        rule.onNodeWithTag("switch_com.a").assertIsOff().performClick()
        assertEquals(BlocklistIntent.ToggleBlocked("com.a"), intents.single())
    }

    @Test
    fun permission_banner_shows_when_access_missing() {
        rule.setContent {
            QuestLogTheme {
                BlocklistScreen(state(permission = false), onIntent = {}, onBack = {}, onGrantAccess = {})
            }
        }
        rule.onNodeWithText("Usage access needed").assertExists()
    }

    @Test
    fun no_banner_when_access_granted() {
        rule.setContent {
            QuestLogTheme {
                BlocklistScreen(state(permission = true), onIntent = {}, onBack = {}, onGrantAccess = {})
            }
        }
        rule.onNodeWithText("Usage access needed").assertDoesNotExist()
    }
}
