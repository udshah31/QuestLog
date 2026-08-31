package com.example.questlog.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.questlog.theme.QuestLogTheme
import com.example.questlog.ui.dashboard.DashboardUiState
import com.example.questlog.ui.today.TodayScreen
import com.example.questlog.ui.today.fakeStats
import com.example.questlog.ui.today.fakeTiles
import com.questlog.domain.model.DailyQuest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TodayScreenTest {

    @get:Rule val compose = createComposeRule()

    private fun state(isPremium: Boolean = false) = DashboardUiState(
        isLoading = false,
        stats = fakeStats(),
        cityTiles = fakeTiles(),
        dailyQuests = listOf(
            DailyQuest("a", "Budget Guardian", "x", 250, 60, true, ""),
            DailyQuest("b", "Master Builder", "y", 300, 70, false, ""),
            DailyQuest("c", "Dawn Discipline", "z", 150, 30, false, ""),
        ),
        isPremium = isPremium,
    )

    @Test
    fun quest_count_and_titles_render() {
        compose.setContent { QuestLogTheme { TodayScreen(state(), {}, {}, {}, {}) } }
        compose.onNodeWithText("1 / 3").assertIsDisplayed()
        compose.onNodeWithText("Budget Guardian").assertIsDisplayed()
    }

    @Test
    fun tapping_realm_strip_invokes_onOpenRealm() {
        var opened = false
        compose.setContent { QuestLogTheme { TodayScreen(state(), {}, {}, { opened = true }, {}) } }
        compose.onNodeWithContentDescription("Your realm, 3 of 6 built. Open.").performClick()
        assertTrue(opened)
    }

    @Test
    fun get_pro_pill_invokes_onOpenPaywall() {
        var paywall = false
        compose.setContent { QuestLogTheme { TodayScreen(state(isPremium = false), {}, { paywall = true }, {}, {}) } }
        compose.onNodeWithText("GET PRO").performClick()
        assertTrue(paywall)
    }
}
