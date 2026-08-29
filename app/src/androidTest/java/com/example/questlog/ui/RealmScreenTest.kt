package com.example.questlog.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.ui.realm.RealmScreen
import com.example.questlog.ui.today.fakeTiles
import com.questlog.domain.model.CityTile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RealmScreenTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun tapping_a_buyable_tile_emits_its_CityTile() {
        var clicked: CityTile? = null
        compose.setContent {
            QuestLogTheme { RealmScreen(fakeTiles(), 250L, onBack = {}, onTileClick = { clicked = it }) }
        }
        compose.onNodeWithText("Zen Garden").performClick()
        assertEquals("garden", clicked?.itemId)
    }

    @Test
    fun back_button_invokes_onBack() {
        var back = false
        compose.setContent {
            QuestLogTheme { RealmScreen(fakeTiles(), 250L, onBack = { back = true }, onTileClick = {}) }
        }
        compose.onNodeWithContentDescription("Back").performClick()
        assertTrue(back)
    }
}
