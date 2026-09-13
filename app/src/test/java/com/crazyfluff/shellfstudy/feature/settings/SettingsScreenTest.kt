package com.crazyfluff.shellfstudy.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.data.ThemeMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Job
import com.crazyfluff.shellfstudy.shared.feature.settings.SettingsActions
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

    /**
     * Renders the screen against a recording stand-in for the ViewModel. Rendering assertions pass only
     * [uiState]; [onNotificationsEnabledChange] is a screen parameter because the route intercepts it
     * to raise the platform permission prompt.
     */
    private fun setContent(
        uiState: SettingsUiState,
        actions: SettingsActions = RecordingSettingsActions(),
        onNotificationsEnabledChange: (Boolean) -> Unit = {},
        onOpenLeaderboard: () -> Unit = {},
        onBack: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            SettingsScreen(
                uiState = uiState,
                actions = actions,
                onNotificationsEnabledChange = onNotificationsEnabledChange,
                onOpenLeaderboard = onOpenLeaderboard,
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
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.LESSON_GOAL_INCREASE).performClick()
        assertThat(actions.lastArgumentOf("onDailyLessonGoalChange")).isEqualTo(16)

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.LESSON_GOAL_DECREASE).performClick()
        assertThat(actions.lastArgumentOf("onDailyLessonGoalChange")).isEqualTo(14)
    }

    @Test
    fun decreaseButton_disabledAtMinimumGoal() {
        setContent(uiState = SettingsUiState(dailyLessonGoal = 1, themeMode = ThemeMode.SYSTEM))

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.LESSON_GOAL_DECREASE).assertIsNotEnabled()
    }

    @Test
    fun selectingThemeOption_invokesCallback() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.THEME_DARK_OPTION).performClick()
        assertThat(actions.lastArgumentOf("onThemeModeChange")).isEqualTo(ThemeMode.DARK)
    }

    @Test
    fun selectingEinkThemeOption_invokesCallback() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.THEME_EINK_OPTION).performClick()
        assertThat(actions.lastArgumentOf("onThemeModeChange")).isEqualTo(ThemeMode.EINK)
    }

    @Test
    fun togglingPitchAccentSwitch_invokesCallback() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, showPitchAccent = true),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.PITCH_ACCENT_TOGGLE).performClick()
        assertThat(actions.lastArgumentOf("onShowPitchAccentChange")).isEqualTo(false)
    }

    @Test
    fun togglingAutoplayAudioSwitch_invokesCallback() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, autoplayPronunciationAudio = true),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.AUTOPLAY_AUDIO_TOGGLE).performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onAutoplayPronunciationAudioChange")).isEqualTo(false)
    }

    @Test
    fun togglingMp3OnlyAudioSwitch_invokesCallback() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, restrictAudioToMp3 = false),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.MP3_ONLY_AUDIO_TOGGLE).performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onRestrictAudioToMp3Change")).isEqualTo(true)
    }

    @Test
    fun togglingShowSubjectTypeLabelSwitch_invokesCallback() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, showSubjectTypeLabel = false),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.SHOW_SUBJECT_TYPE_LABEL_TOGGLE).performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onShowSubjectTypeLabelChange")).isEqualTo(true)
    }

    @Test
    fun togglingShowTotalTimerSwitch_invokesCallback() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, showTotalTimer = false),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.SHOW_TOTAL_TIMER_TOGGLE).performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onShowTotalTimerChange")).isEqualTo(true)
    }

    @Test
    fun togglingShowQuestionTimerSwitch_invokesCallback() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, showQuestionTimer = false),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.SHOW_QUESTION_TIMER_TOGGLE).performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onShowQuestionTimerChange")).isEqualTo(true)
    }

    @Test
    fun togglingUseJapaneseKeyboardSwitch_invokesCallback() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, useJapaneseKeyboard = false),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.JAPANESE_KEYBOARD_TOGGLE).performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onUseJapaneseKeyboardChange")).isEqualTo(true)
    }

    @Test
    fun togglingCloseEnoughAnswersSwitch_invokesCallback() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, closeEnoughAnswersEnabled = true),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.CLOSE_ENOUGH_ANSWERS_TOGGLE).performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onCloseEnoughAnswersEnabledChange")).isEqualTo(false)
    }

    @Test
    fun togglingAnswerReadingPitchAccentSwitch_invokesCallback() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, showAnswerReadingPitchAccent = false),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.ANSWER_READING_PITCH_ACCENT_TOGGLE).performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onShowAnswerReadingPitchAccentChange")).isEqualTo(true)
    }

    @Test
    fun togglingHideContextSentenceTranslationsSwitch_invokesCallback() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, hideContextSentenceTranslations = true),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.HIDE_CONTEXT_SENTENCE_TRANSLATIONS_TOGGLE)
            .performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onHideContextSentenceTranslationsChange")).isEqualTo(false)
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
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(notificationsEnabled = true, reviewsBacklogEnabled = true, backlogThreshold = 50),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.BACKLOG_THRESHOLD_INCREASE).performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onBacklogThresholdChange")).isEqualTo(55)

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.BACKLOG_THRESHOLD_DECREASE).performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onBacklogThresholdChange")).isEqualTo(45)
    }

    @Test
    fun dailyReminderHourStepper_wrapsAroundMidnight() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(notificationsEnabled = true, dailyReminderEnabled = true, dailyReminderHour = 23),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.DAILY_REMINDER_HOUR_INCREASE).performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onDailyReminderHourChange")).isEqualTo(0)
    }

    @Test
    fun quietHoursSteppers_invokeCallbacks() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(notificationsEnabled = true, quietHoursEnabled = true, quietHoursStartHour = 22, quietHoursEndHour = 7),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.QUIET_HOURS_START_INCREASE).performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onQuietHoursStartHourChange")).isEqualTo(23)

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.QUIET_HOURS_END_DECREASE).performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onQuietHoursEndHourChange")).isEqualTo(6)
    }

    @Test
    fun fullRefreshRow_showsConfirmationBeforeInvokingCallback() {
        val actions = RecordingSettingsActions()
        setContent(uiState = SettingsUiState(), actions = actions)

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.FULL_REFRESH_ROW).performScrollTo().performClick()
        assertThat(actions.calls).doesNotContain("onFullRefreshRequested")

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.FULL_REFRESH_CONFIRM_BUTTON).performClick()
        assertThat(actions.calls).contains("onFullRefreshRequested")
    }

    @Test
    fun fullRefreshRow_showsProgressAndIsDisabledWhileRefreshing() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(isFullRefreshing = true),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.FULL_REFRESH_PROGRESS).performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.FULL_REFRESH_ROW).performClick()
        assertThat(actions.calls).doesNotContain("onFullRefreshRequested")
    }

    @Test
    fun fullRefreshRow_showsErrorMessageOnFailure() {
        setContent(uiState = SettingsUiState(fullRefreshError = "WaniKani API error (500)"))

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.FULL_REFRESH_ERROR_TEXT).performScrollTo().assertIsDisplayed()
    }
}

/** A [SettingsActions] that records what it was asked to do — see the Lesson and Review tests. */
private class RecordingSettingsActions : SettingsActions {
    val calls = mutableListOf<String>()
    private val arguments = mutableListOf<Any?>()

    /** The argument passed to the most recent [name] call. */
    fun lastArgumentOf(name: String): Any? = arguments[calls.lastIndexOf(name)]

    private fun record(name: String, argument: Any? = null) {
        calls += name
        arguments += argument
    }

    override fun onDailyLessonGoalChange(goal: Int) = record("onDailyLessonGoalChange", goal)
    override fun onLessonBatchSizeChange(size: Int) = record("onLessonBatchSizeChange", size)
    override fun onThemeModeChange(mode: ThemeMode) = record("onThemeModeChange", mode)
    override fun onShowPitchAccentChange(enabled: Boolean) = record("onShowPitchAccentChange", enabled)
    override fun onAutoplayPronunciationAudioChange(enabled: Boolean) = record("onAutoplayPronunciationAudioChange", enabled)
    override fun onRestrictAudioToMp3Change(enabled: Boolean) = record("onRestrictAudioToMp3Change", enabled)
    override fun onShowSubjectTypeLabelChange(enabled: Boolean) = record("onShowSubjectTypeLabelChange", enabled)
    override fun onShowTotalTimerChange(enabled: Boolean) = record("onShowTotalTimerChange", enabled)
    override fun onShowQuestionTimerChange(enabled: Boolean) = record("onShowQuestionTimerChange", enabled)
    override fun onShowStrokeOrderChange(enabled: Boolean) = record("onShowStrokeOrderChange", enabled)
    override fun onUseJapaneseKeyboardChange(enabled: Boolean) = record("onUseJapaneseKeyboardChange", enabled)
    override fun onCloseEnoughAnswersEnabledChange(enabled: Boolean) = record("onCloseEnoughAnswersEnabledChange", enabled)
    override fun onShowAnswerReadingPitchAccentChange(enabled: Boolean) = record("onShowAnswerReadingPitchAccentChange", enabled)
    override fun onHideContextSentenceTranslationsChange(enabled: Boolean) = record("onHideContextSentenceTranslationsChange", enabled)
    override fun onReviewsAvailableEnabledChange(enabled: Boolean) = record("onReviewsAvailableEnabledChange", enabled)
    override fun onReviewsBacklogEnabledChange(enabled: Boolean) = record("onReviewsBacklogEnabledChange", enabled)
    override fun onBacklogThresholdChange(threshold: Int) = record("onBacklogThresholdChange", threshold)
    override fun onDailyReminderEnabledChange(enabled: Boolean) = record("onDailyReminderEnabledChange", enabled)
    override fun onDailyReminderHourChange(hour: Int): Job {
        record("onDailyReminderHourChange", hour)
        return Job()
    }
    override fun onQuietHoursEnabledChange(enabled: Boolean) = record("onQuietHoursEnabledChange", enabled)
    override fun onQuietHoursStartHourChange(hour: Int) = record("onQuietHoursStartHourChange", hour)
    override fun onQuietHoursEndHourChange(hour: Int) = record("onQuietHoursEndHourChange", hour)
    override fun onFullRefreshRequested() = record("onFullRefreshRequested")
}
