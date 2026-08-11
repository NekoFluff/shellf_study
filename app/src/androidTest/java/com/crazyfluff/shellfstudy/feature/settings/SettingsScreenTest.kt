package com.crazyfluff.shellfstudy.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.crazyfluff.shellfstudy.core.data.ThemeMode
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(
        uiState: SettingsUiState,
        onDailyLessonGoalChange: (Int) -> Unit = {},
        onThemeModeChange: (ThemeMode) -> Unit = {},
        onShowPitchAccentChange: (Boolean) -> Unit = {},
        onAutoplayPronunciationAudioChange: (Boolean) -> Unit = {},
        onNotificationsEnabledChange: (Boolean) -> Unit = {},
        onReviewsAvailableEnabledChange: (Boolean) -> Unit = {},
        onReviewsBacklogEnabledChange: (Boolean) -> Unit = {},
        onBacklogThresholdChange: (Int) -> Unit = {},
        onDailyReminderEnabledChange: (Boolean) -> Unit = {},
        onDailyReminderHourChange: (Int) -> Unit = {},
        onQuietHoursEnabledChange: (Boolean) -> Unit = {},
        onQuietHoursStartHourChange: (Int) -> Unit = {},
        onQuietHoursEndHourChange: (Int) -> Unit = {},
        onBack: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            SettingsScreen(
                uiState = uiState,
                onDailyLessonGoalChange = onDailyLessonGoalChange,
                onThemeModeChange = onThemeModeChange,
                onShowPitchAccentChange = onShowPitchAccentChange,
                onAutoplayPronunciationAudioChange = onAutoplayPronunciationAudioChange,
                onNotificationsEnabledChange = onNotificationsEnabledChange,
                onReviewsAvailableEnabledChange = onReviewsAvailableEnabledChange,
                onReviewsBacklogEnabledChange = onReviewsBacklogEnabledChange,
                onBacklogThresholdChange = onBacklogThresholdChange,
                onDailyReminderEnabledChange = onDailyReminderEnabledChange,
                onDailyReminderHourChange = onDailyReminderHourChange,
                onQuietHoursEnabledChange = onQuietHoursEnabledChange,
                onQuietHoursStartHourChange = onQuietHoursStartHourChange,
                onQuietHoursEndHourChange = onQuietHoursEndHourChange,
                onBack = onBack
            )
        }
    }

    @Test
    fun showsCurrentDailyLessonGoalAndThemeSelection() {
        setContent(uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.DARK))

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.LESSON_GOAL_VALUE).assertIsDisplayed()
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.THEME_DARK_OPTION).assertIsDisplayed()
    }

    @Test
    fun increaseAndDecreaseButtons_invokeCallbackWithAdjustedGoal() {
        var lastGoal = -1
        setContent(
            uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM),
            onDailyLessonGoalChange = { lastGoal = it }
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.LESSON_GOAL_INCREASE).performClick()
        assert(lastGoal == 16)

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.LESSON_GOAL_DECREASE).performClick()
        assert(lastGoal == 14)
    }

    @Test
    fun decreaseButton_disabledAtMinimumGoal() {
        setContent(uiState = SettingsUiState(dailyLessonGoal = 1, themeMode = ThemeMode.SYSTEM))

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.LESSON_GOAL_DECREASE).assertIsNotEnabled()
    }

    @Test
    fun selectingThemeOption_invokesCallback() {
        var selectedMode: ThemeMode? = null
        setContent(
            uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM),
            onThemeModeChange = { selectedMode = it }
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.THEME_DARK_OPTION).performClick()
        assert(selectedMode == ThemeMode.DARK)
    }

    @Test
    fun selectingEinkThemeOption_invokesCallback() {
        var selectedMode: ThemeMode? = null
        setContent(
            uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM),
            onThemeModeChange = { selectedMode = it }
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.THEME_EINK_OPTION).performClick()
        assert(selectedMode == ThemeMode.EINK)
    }

    @Test
    fun togglingPitchAccentSwitch_invokesCallback() {
        var showPitchAccent: Boolean? = null
        setContent(
            uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, showPitchAccent = true),
            onShowPitchAccentChange = { showPitchAccent = it }
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.PITCH_ACCENT_TOGGLE).performClick()
        assert(showPitchAccent == false)
    }

    @Test
    fun togglingAutoplayAudioSwitch_invokesCallback() {
        var autoplay: Boolean? = null
        setContent(
            uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, autoplayPronunciationAudio = true),
            onAutoplayPronunciationAudioChange = { autoplay = it }
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.AUTOPLAY_AUDIO_TOGGLE).performClick()
        assert(autoplay == false)
    }

    @Test
    fun backButton_invokesCallback() {
        var wentBack = false
        setContent(uiState = SettingsUiState(), onBack = { wentBack = true })

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.BACK_BUTTON).performClick()
        assert(wentBack)
    }

    @Test
    fun togglingNotificationsMasterSwitch_invokesCallback() {
        var enabled: Boolean? = null
        setContent(
            uiState = SettingsUiState(notificationsEnabled = false),
            onNotificationsEnabledChange = { enabled = it }
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.NOTIFICATIONS_MASTER_TOGGLE).performClick()
        assert(enabled == true)
    }

    @Test
    fun categoryToggles_areHiddenWhenNotificationsAreDisabled() {
        setContent(uiState = SettingsUiState(notificationsEnabled = false))

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.REVIEWS_AVAILABLE_TOGGLE).assertDoesNotExist()
    }

    @Test
    fun categoryToggles_areShownWhenNotificationsAreEnabled() {
        setContent(uiState = SettingsUiState(notificationsEnabled = true))

        // The whole screen is one scrolling column (SettingsScreen.kt), so these notification
        // sub-toggles can sit below the fold depending on device screen height.
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.REVIEWS_AVAILABLE_TOGGLE).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.DAILY_REMINDER_TOGGLE).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.QUIET_HOURS_TOGGLE).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun backlogThresholdStepper_invokesCallbackWithStepOfFive() {
        var threshold = -1
        setContent(
            uiState = SettingsUiState(notificationsEnabled = true, reviewsBacklogEnabled = true, backlogThreshold = 50),
            onBacklogThresholdChange = { threshold = it }
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.BACKLOG_THRESHOLD_INCREASE).performScrollTo().performClick()
        assert(threshold == 55)

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.BACKLOG_THRESHOLD_DECREASE).performScrollTo().performClick()
        assert(threshold == 45)
    }

    @Test
    fun dailyReminderHourStepper_wrapsAroundMidnight() {
        var hour = -1
        setContent(
            uiState = SettingsUiState(notificationsEnabled = true, dailyReminderEnabled = true, dailyReminderHour = 23),
            onDailyReminderHourChange = { hour = it }
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.DAILY_REMINDER_HOUR_INCREASE).performScrollTo().performClick()
        assert(hour == 0)
    }

    @Test
    fun quietHoursSteppers_invokeCallbacks() {
        var start: Int? = null
        var end: Int? = null
        setContent(
            uiState = SettingsUiState(notificationsEnabled = true, quietHoursEnabled = true, quietHoursStartHour = 22, quietHoursEndHour = 7),
            onQuietHoursStartHourChange = { start = it },
            onQuietHoursEndHourChange = { end = it }
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.QUIET_HOURS_START_INCREASE).performScrollTo().performClick()
        assert(start == 23)

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.QUIET_HOURS_END_DECREASE).performScrollTo().performClick()
        assert(end == 6)
    }
}
