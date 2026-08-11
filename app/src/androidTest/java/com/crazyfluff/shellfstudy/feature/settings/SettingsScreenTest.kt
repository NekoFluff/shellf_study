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
                onShowPitchAccentChange = {},
                onAutoplayPronunciationAudioChange = {},
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
                onShowPitchAccentChange = {},
                onAutoplayPronunciationAudioChange = {},
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
                onShowPitchAccentChange = {},
                onAutoplayPronunciationAudioChange = {},
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
                onShowPitchAccentChange = {},
                onAutoplayPronunciationAudioChange = {},
                onBack = {}
            )
        }

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.THEME_DARK_OPTION).performClick()
        assert(selectedMode == ThemeMode.DARK)
    }

    @Test
    fun togglingPitchAccentSwitch_invokesCallback() {
        var showPitchAccent: Boolean? = null
        composeTestRule.setContent {
            SettingsScreen(
                uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, showPitchAccent = true),
                onDailyLessonGoalChange = {},
                onThemeModeChange = {},
                onShowPitchAccentChange = { showPitchAccent = it },
                onAutoplayPronunciationAudioChange = {},
                onBack = {}
            )
        }

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.PITCH_ACCENT_TOGGLE).performClick()
        assert(showPitchAccent == false)
    }

    @Test
    fun togglingAutoplayAudioSwitch_invokesCallback() {
        var autoplay: Boolean? = null
        composeTestRule.setContent {
            SettingsScreen(
                uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, autoplayPronunciationAudio = true),
                onDailyLessonGoalChange = {},
                onThemeModeChange = {},
                onShowPitchAccentChange = {},
                onAutoplayPronunciationAudioChange = { autoplay = it },
                onBack = {}
            )
        }

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.AUTOPLAY_AUDIO_TOGGLE).performClick()
        assert(autoplay == false)
    }

    @Test
    fun backButton_invokesCallback() {
        var wentBack = false
        composeTestRule.setContent {
            SettingsScreen(
                uiState = SettingsUiState(),
                onDailyLessonGoalChange = {},
                onThemeModeChange = {},
                onShowPitchAccentChange = {},
                onAutoplayPronunciationAudioChange = {},
                onBack = { wentBack = true }
            )
        }

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.BACK_BUTTON).performClick()
        assert(wentBack)
    }
}
