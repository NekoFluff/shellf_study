package com.crazyfluff.shellfstudy.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.data.ThemeMode
import com.crazyfluff.shellfstudy.shared.feature.settings.SettingsScreen
import com.crazyfluff.shellfstudy.shared.feature.settings.SettingsScreenTestTags
import com.crazyfluff.shellfstudy.shared.feature.settings.SettingsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Runs under Robolectric (JVM) — this screen is driven purely by state, no device features needed.
 * Pinned to SDK 35: Robolectric 4.15.1 doesn't yet have shadows for this project's targetSdk (37).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(
        uiState: SettingsUiState,
        onDailyLessonGoalChange: (Int) -> Unit = {},
        onThemeModeChange: (ThemeMode) -> Unit = {},
        onShowPitchAccentChange: (Boolean) -> Unit = {},
        onAutoplayPronunciationAudioChange: (Boolean) -> Unit = {},
        onRestrictAudioToMp3Change: (Boolean) -> Unit = {},
        onShowSubjectTypeLabelChange: (Boolean) -> Unit = {},
        onShowTotalTimerChange: (Boolean) -> Unit = {},
        onShowQuestionTimerChange: (Boolean) -> Unit = {},
        onUseJapaneseKeyboardChange: (Boolean) -> Unit = {},
        onNotificationsEnabledChange: (Boolean) -> Unit = {},
        onReviewsAvailableEnabledChange: (Boolean) -> Unit = {},
        onReviewsBacklogEnabledChange: (Boolean) -> Unit = {},
        onBacklogThresholdChange: (Int) -> Unit = {},
        onDailyReminderEnabledChange: (Boolean) -> Unit = {},
        onDailyReminderHourChange: (Int) -> Unit = {},
        onQuietHoursEnabledChange: (Boolean) -> Unit = {},
        onQuietHoursStartHourChange: (Int) -> Unit = {},
        onQuietHoursEndHourChange: (Int) -> Unit = {},
        onFullRefreshRequested: () -> Unit = {},
        onBack: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            SettingsScreen(
                uiState = uiState,
                onDailyLessonGoalChange = onDailyLessonGoalChange,
                onThemeModeChange = onThemeModeChange,
                onShowPitchAccentChange = onShowPitchAccentChange,
                onAutoplayPronunciationAudioChange = onAutoplayPronunciationAudioChange,
                onRestrictAudioToMp3Change = onRestrictAudioToMp3Change,
                onShowSubjectTypeLabelChange = onShowSubjectTypeLabelChange,
                onShowTotalTimerChange = onShowTotalTimerChange,
                onShowQuestionTimerChange = onShowQuestionTimerChange,
                onShowStrokeOrderChange = {},
                onUseJapaneseKeyboardChange = onUseJapaneseKeyboardChange,
                onNotificationsEnabledChange = onNotificationsEnabledChange,
                onReviewsAvailableEnabledChange = onReviewsAvailableEnabledChange,
                onReviewsBacklogEnabledChange = onReviewsBacklogEnabledChange,
                onBacklogThresholdChange = onBacklogThresholdChange,
                onDailyReminderEnabledChange = onDailyReminderEnabledChange,
                onDailyReminderHourChange = onDailyReminderHourChange,
                onQuietHoursEnabledChange = onQuietHoursEnabledChange,
                onQuietHoursStartHourChange = onQuietHoursStartHourChange,
                onQuietHoursEndHourChange = onQuietHoursEndHourChange,
                onFullRefreshRequested = onFullRefreshRequested,
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
    fun togglingMp3OnlyAudioSwitch_invokesCallback() {
        var restrictToMp3: Boolean? = null
        setContent(
            uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, restrictAudioToMp3 = false),
            onRestrictAudioToMp3Change = { restrictToMp3 = it }
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.MP3_ONLY_AUDIO_TOGGLE).performScrollTo().performClick()
        assert(restrictToMp3 == true)
    }

    @Test
    fun togglingShowSubjectTypeLabelSwitch_invokesCallback() {
        var showLabel: Boolean? = null
        setContent(
            uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, showSubjectTypeLabel = false),
            onShowSubjectTypeLabelChange = { showLabel = it }
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.SHOW_SUBJECT_TYPE_LABEL_TOGGLE).performScrollTo().performClick()
        assert(showLabel == true)
    }

    @Test
    fun togglingShowTotalTimerSwitch_invokesCallback() {
        var showTimer: Boolean? = null
        setContent(
            uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, showTotalTimer = false),
            onShowTotalTimerChange = { showTimer = it }
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.SHOW_TOTAL_TIMER_TOGGLE).performScrollTo().performClick()
        assert(showTimer == true)
    }

    @Test
    fun togglingShowQuestionTimerSwitch_invokesCallback() {
        var showTimer: Boolean? = null
        setContent(
            uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, showQuestionTimer = false),
            onShowQuestionTimerChange = { showTimer = it }
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.SHOW_QUESTION_TIMER_TOGGLE).performScrollTo().performClick()
        assert(showTimer == true)
    }

    @Test
    fun togglingUseJapaneseKeyboardSwitch_invokesCallback() {
        var useJapaneseKeyboard: Boolean? = null
        setContent(
            uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, useJapaneseKeyboard = false),
            onUseJapaneseKeyboardChange = { useJapaneseKeyboard = it }
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.JAPANESE_KEYBOARD_TOGGLE).performScrollTo().performClick()
        assert(useJapaneseKeyboard == true)
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

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.NOTIFICATIONS_MASTER_TOGGLE).performScrollTo().performClick()
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

    @Test
    fun fullRefreshRow_showsConfirmationBeforeInvokingCallback() {
        var refreshed = false
        setContent(uiState = SettingsUiState(), onFullRefreshRequested = { refreshed = true })

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.FULL_REFRESH_ROW).performScrollTo().performClick()
        assert(!refreshed)

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.FULL_REFRESH_CONFIRM_BUTTON).performClick()
        assert(refreshed)
    }

    @Test
    fun fullRefreshRow_showsProgressAndIsDisabledWhileRefreshing() {
        var refreshed = false
        setContent(
            uiState = SettingsUiState(isFullRefreshing = true),
            onFullRefreshRequested = { refreshed = true }
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.FULL_REFRESH_PROGRESS).performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.FULL_REFRESH_ROW).performClick()
        assert(!refreshed)
    }

    @Test
    fun fullRefreshRow_showsErrorMessageOnFailure() {
        setContent(uiState = SettingsUiState(fullRefreshError = "WaniKani API error (500)"))

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.FULL_REFRESH_ERROR_TEXT).performScrollTo().assertIsDisplayed()
    }
}
