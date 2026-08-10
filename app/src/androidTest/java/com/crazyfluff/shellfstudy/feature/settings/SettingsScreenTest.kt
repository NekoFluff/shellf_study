package com.crazyfluff.shellfstudy.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.crazyfluff.shellfstudy.core.data.ThemeMode
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsCurrentDailyLessonGoalAndThemeSelection() {
        composeTestRule.setContent {
            SettingsScreen(
                uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.DARK),
                onDailyLessonGoalChange = {},
                onThemeModeChange = {},
                onBack = {}
            )
        }

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.LESSON_GOAL_VALUE).assertIsDisplayed()
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.THEME_DARK_OPTION).assertIsDisplayed()
    }

    @Test
    fun increaseAndDecreaseButtons_invokeCallbackWithAdjustedGoal() {
        var lastGoal = -1
        composeTestRule.setContent {
            SettingsScreen(
                uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM),
                onDailyLessonGoalChange = { lastGoal = it },
                onThemeModeChange = {},
                onBack = {}
            )
        }

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.LESSON_GOAL_INCREASE).performClick()
        assert(lastGoal == 16)

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.LESSON_GOAL_DECREASE).performClick()
        assert(lastGoal == 14)
    }

    @Test
    fun decreaseButton_disabledAtMinimumGoal() {
        composeTestRule.setContent {
            SettingsScreen(
                uiState = SettingsUiState(dailyLessonGoal = 1, themeMode = ThemeMode.SYSTEM),
                onDailyLessonGoalChange = {},
                onThemeModeChange = {},
                onBack = {}
            )
        }

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.LESSON_GOAL_DECREASE).assertIsNotEnabled()
    }

    @Test
    fun selectingThemeOption_invokesCallback() {
        var selectedMode: ThemeMode? = null
        composeTestRule.setContent {
            SettingsScreen(
                uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM),
                onDailyLessonGoalChange = {},
                onThemeModeChange = { selectedMode = it },
                onBack = {}
            )
        }

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.THEME_DARK_OPTION).performClick()
        assert(selectedMode == ThemeMode.DARK)
    }

    @Test
    fun backButton_invokesCallback() {
        var wentBack = false
        composeTestRule.setContent {
            SettingsScreen(
                uiState = SettingsUiState(),
                onDailyLessonGoalChange = {},
                onThemeModeChange = {},
                onBack = { wentBack = true }
            )
        }

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.BACK_BUTTON).performClick()
        assert(wentBack)
    }
}
