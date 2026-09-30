package com.crazyfluff.shellfstudy.feature.settings

import com.crazyfluff.shellfstudy.shared.data.DAILY_STUDY_MINUTES_GOAL_RANGE
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.data.ThemeMode
import com.crazyfluff.shellfstudy.shared.data.model.ReviewPriority
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Job
import com.crazyfluff.shellfstudy.shared.designsystem.AppVersion
import com.crazyfluff.shellfstudy.shared.feature.settings.AccountSummary
import com.crazyfluff.shellfstudy.shared.feature.settings.SettingsActions
import com.crazyfluff.shellfstudy.shared.feature.settings.SettingsScreen
import com.crazyfluff.shellfstudy.shared.feature.settings.SettingsScreenTestTags
import com.crazyfluff.shellfstudy.shared.data.AppSettings
import com.crazyfluff.shellfstudy.shared.data.NotificationSettings
import com.crazyfluff.shellfstudy.shared.feature.settings.FullRefreshStatus
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
@Config(qualifiers = "w411dp-h891dp")
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
        onBack: () -> Unit = {},
        appVersion: AppVersion? = null
    ) {
        composeTestRule.setContent {
            SettingsScreen(
                uiState = uiState,
                actions = actions,
                onNotificationsEnabledChange = onNotificationsEnabledChange,
                onOpenLeaderboard = onOpenLeaderboard,
                onBack = onBack,
                appVersion = appVersion
            )
        }
    }
    @Test
    fun showsCurrentDailyLessonGoalAndThemeSelection() {
        setContent(uiState = SettingsUiState(app = AppSettings(dailyLessonGoal = 15, themeMode = ThemeMode.DARK)))

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.LESSON_GOAL_VALUE).assertIsDisplayed()
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.THEME_DARK_OPTION).performScrollTo().assertIsSelected()
    }

    @Test
    fun increaseAndDecreaseButtons_invokeCallbackWithAdjustedGoal() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(app = AppSettings(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM)),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.LESSON_GOAL_INCREASE).performClick()
        assertThat(actions.lastArgumentOf("onDailyLessonGoalChange")).isEqualTo(16)

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.LESSON_GOAL_DECREASE).performClick()
        assertThat(actions.lastArgumentOf("onDailyLessonGoalChange")).isEqualTo(14)
    }

    @Test
    fun studyTimeGoalStepper_movesInFiveMinuteSteps() {
        val actions = RecordingSettingsActions()
        setContent(uiState = SettingsUiState(app = AppSettings(dailyStudyMinutesGoal = 30)), actions = actions)

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.STUDY_GOAL_VALUE)
            .performScrollTo()
            .assertTextEquals("30 min")
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.STUDY_GOAL_INCREASE).performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onDailyStudyMinutesGoalChange")).isEqualTo(35)

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.STUDY_GOAL_DECREASE).performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onDailyStudyMinutesGoalChange")).isEqualTo(25)
    }

    @Test
    fun studyTimeGoalStepper_stopsAtTheEndsOfTheRange() {
        setContent(
            uiState = SettingsUiState(app = AppSettings(dailyStudyMinutesGoal = DAILY_STUDY_MINUTES_GOAL_RANGE.last))
        )
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.STUDY_GOAL_INCREASE).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun studyTimeGoalDecrease_disabledAtTheMinimum() {
        setContent(
            uiState = SettingsUiState(app = AppSettings(dailyStudyMinutesGoal = DAILY_STUDY_MINUTES_GOAL_RANGE.first))
        )
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.STUDY_GOAL_DECREASE).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun decreaseButton_disabledAtMinimumGoal() {
        setContent(uiState = SettingsUiState(app = AppSettings(dailyLessonGoal = 1, themeMode = ThemeMode.SYSTEM)))

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.LESSON_GOAL_DECREASE).assertIsNotEnabled()
    }

    @Test
    fun backlogThresholdStepper_decreaseButton_disabledAtMinimum() {
        setContent(
            uiState = SettingsUiState(notifications = NotificationSettings(notificationsEnabled = true, reviewsBacklogEnabled = true, backlogThreshold = 5))
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.BACKLOG_THRESHOLD_DECREASE)
            .performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun backlogThresholdStepper_increaseButton_disabledAtMaximum() {
        setContent(
            uiState = SettingsUiState(notifications = NotificationSettings(notificationsEnabled = true, reviewsBacklogEnabled = true, backlogThreshold = 500))
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.BACKLOG_THRESHOLD_INCREASE)
            .performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun reviewPriorityOptions_showCurrentSelectionAndInvokeCallback() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(app = AppSettings(reviewPriority = ReviewPriority.DEFAULT)),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.reviewPriorityOptionTag(ReviewPriority.DEFAULT))
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.reviewPriorityOptionTag(ReviewPriority.RANK_UP))
            .performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onReviewPriorityChange")).isEqualTo(ReviewPriority.RANK_UP)
    }

    @Test
    fun selectingThemeOption_invokesCallback() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(app = AppSettings(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM)),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.THEME_DARK_OPTION).performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onThemeModeChange")).isEqualTo(ThemeMode.DARK)
    }

    @Test
    fun selectingEinkThemeOption_invokesCallback() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(app = AppSettings(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM)),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.THEME_EINK_OPTION).performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onThemeModeChange")).isEqualTo(ThemeMode.EINK)
    }

    @Test
    fun togglingPitchAccentSwitch_invokesCallback() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(app = AppSettings(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, showPitchAccent = true)),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.PITCH_ACCENT_TOGGLE).performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onShowPitchAccentChange")).isEqualTo(false)
    }

    @Test
    fun togglingAutoplayAudioSwitch_invokesCallback() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(app = AppSettings(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, autoplayPronunciationAudio = true)),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.AUTOPLAY_AUDIO_TOGGLE).performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onAutoplayPronunciationAudioChange")).isEqualTo(false)
    }

    @Test
    fun togglingMp3OnlyAudioSwitch_invokesCallback() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(app = AppSettings(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, restrictAudioToMp3 = false)),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.MP3_ONLY_AUDIO_TOGGLE).performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onRestrictAudioToMp3Change")).isEqualTo(true)
    }

    @Test
    fun togglingShowSubjectTypeLabelSwitch_invokesCallback() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(app = AppSettings(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, showSubjectTypeLabel = false)),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.SHOW_SUBJECT_TYPE_LABEL_TOGGLE).performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onShowSubjectTypeLabelChange")).isEqualTo(true)
    }

    @Test
    fun togglingShowTotalTimerSwitch_invokesCallback() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(app = AppSettings(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, showTotalTimer = false)),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.SHOW_TOTAL_TIMER_TOGGLE).performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onShowTotalTimerChange")).isEqualTo(true)
    }

    @Test
    fun togglingShowQuestionTimerSwitch_invokesCallback() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(app = AppSettings(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, showQuestionTimer = false)),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.SHOW_QUESTION_TIMER_TOGGLE).performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onShowQuestionTimerChange")).isEqualTo(true)
    }

    @Test
    fun togglingUseJapaneseKeyboardSwitch_invokesCallback() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(app = AppSettings(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, useJapaneseKeyboard = false)),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.JAPANESE_KEYBOARD_TOGGLE).performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onUseJapaneseKeyboardChange")).isEqualTo(true)
    }

    @Test
    fun togglingCloseEnoughAnswersSwitch_invokesCallback() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(app = AppSettings(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, closeEnoughAnswersEnabled = true)),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.CLOSE_ENOUGH_ANSWERS_TOGGLE).performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onCloseEnoughAnswersEnabledChange")).isEqualTo(false)
    }

    @Test
    fun togglingRequireTapToRevealMeaningAnswerSwitch_invokesCallback() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(app = AppSettings(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, requireTapToRevealMeaningAnswer = false)),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.REQUIRE_TAP_TO_REVEAL_MEANING_ANSWER_TOGGLE).performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onRequireTapToRevealMeaningAnswerChange")).isEqualTo(true)
    }

    @Test
    fun togglingRequireTapToRevealReadingAnswerSwitch_invokesCallback() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(app = AppSettings(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, requireTapToRevealReadingAnswer = false)),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.REQUIRE_TAP_TO_REVEAL_READING_ANSWER_TOGGLE).performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onRequireTapToRevealReadingAnswerChange")).isEqualTo(true)
    }

    @Test
    fun togglingAnswerReadingPitchAccentSwitch_invokesCallback() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(app = AppSettings(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, showAnswerReadingPitchAccent = false)),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.ANSWER_READING_PITCH_ACCENT_TOGGLE).performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onShowAnswerReadingPitchAccentChange")).isEqualTo(true)
    }

    @Test
    fun togglingHideContextSentenceTranslationsSwitch_invokesCallback() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(app = AppSettings(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, hideContextSentenceTranslations = true)),
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
            uiState = SettingsUiState(notifications = NotificationSettings(notificationsEnabled = false)),
            onNotificationsEnabledChange = { enabled = it }
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.NOTIFICATIONS_MASTER_TOGGLE).performScrollTo().performClick()
        assert(enabled == true)
    }

    @Test
    fun categoryToggles_areDisabledWhenNotificationsAreDisabled() {
        setContent(uiState = SettingsUiState(notifications = NotificationSettings(notificationsEnabled = false)))

        // Visible but disabled, so you can see what turning notifications on would offer.
        listOf(
            SettingsScreenTestTags.REVIEWS_AVAILABLE_TOGGLE,
            SettingsScreenTestTags.DAILY_REMINDER_TOGGLE,
            SettingsScreenTestTags.QUIET_HOURS_TOGGLE
        ).forEach { tag ->
            composeTestRule.onNodeWithTag(tag).performScrollTo().assertIsNotEnabled()
        }
    }

    @Test
    fun dependentRows_areHiddenWhileTheirParentIsOff() {
        setContent(
            uiState = SettingsUiState(
                notifications = NotificationSettings(
                    notificationsEnabled = true,
                    reviewsBacklogEnabled = false,
                    dailyReminderEnabled = false,
                    quietHoursEnabled = false
                )
            )
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.BACKLOG_THRESHOLD_VALUE).assertDoesNotExist()
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.DAILY_REMINDER_TIME_ROW).assertDoesNotExist()
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.QUIET_HOURS_START_ROW).assertDoesNotExist()
    }

    @Test
    fun dependentRows_areHiddenWhileNotificationsAreOff_evenIfTheirParentIsOn() {
        setContent(
            uiState = SettingsUiState(
                notifications = NotificationSettings(
                    notificationsEnabled = false,
                    reviewsBacklogEnabled = true,
                    dailyReminderEnabled = true
                )
            )
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.BACKLOG_THRESHOLD_VALUE).assertDoesNotExist()
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.DAILY_REMINDER_TIME_ROW).assertDoesNotExist()
    }

    @Test
    fun categoryToggles_areShownWhenNotificationsAreEnabled() {
        setContent(uiState = SettingsUiState(notifications = NotificationSettings(notificationsEnabled = true)))

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
            uiState = SettingsUiState(notifications = NotificationSettings(notificationsEnabled = true, reviewsBacklogEnabled = true, backlogThreshold = 50)),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.BACKLOG_THRESHOLD_INCREASE).performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onBacklogThresholdChange")).isEqualTo(55)

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.BACKLOG_THRESHOLD_DECREASE).performScrollTo().performClick()
        assertThat(actions.lastArgumentOf("onBacklogThresholdChange")).isEqualTo(45)
    }

    @Test
    fun dailyReminderTime_opensHourPickerAndInvokesCallbackWithPickedHour() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(
                notifications = NotificationSettings(
                    notificationsEnabled = true,
                    dailyReminderEnabled = true,
                    dailyReminderHour = 19
                )
            ),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.DAILY_REMINDER_TIME_ROW).performScrollTo().performClick()
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.hourOptionTag(19)).assertIsSelected()
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.hourOptionTag(20)).performClick()

        assertThat(actions.lastArgumentOf("onDailyReminderHourChange")).isEqualTo(20)
        // Picking closes the picker. There's no separate confirm step.
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.hourOptionTag(20)).assertDoesNotExist()
    }

    @Test
    fun hourPicker_cancelLeavesTheHourUnchanged() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(
                notifications = NotificationSettings(notificationsEnabled = true, dailyReminderEnabled = true)
            ),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.DAILY_REMINDER_TIME_ROW).performScrollTo().performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()

        assertThat(actions.calls).doesNotContain("onDailyReminderHourChange")
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.hourOptionTag(0)).assertDoesNotExist()
    }

    @Test
    fun quietHoursRows_eachOpenThePickerForTheirOwnEnd() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(notifications = NotificationSettings(notificationsEnabled = true, quietHoursEnabled = true, quietHoursStartHour = 22, quietHoursEndHour = 7)),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.QUIET_HOURS_START_ROW).performScrollTo().performClick()
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.hourOptionTag(23)).performClick()
        assertThat(actions.lastArgumentOf("onQuietHoursStartHourChange")).isEqualTo(23)

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.QUIET_HOURS_END_ROW).performScrollTo().performClick()
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.hourOptionTag(6)).performClick()
        assertThat(actions.lastArgumentOf("onQuietHoursEndHourChange")).isEqualTo(6)
        assertThat(actions.calls.count { it == "onQuietHoursStartHourChange" }).isEqualTo(1)
    }

    @Test
    fun timerSegments_toggleTheirOwnSettingIndependently() {
        val actions = RecordingSettingsActions()
        setContent(
            uiState = SettingsUiState(app = AppSettings(showTotalTimer = true, showQuestionTimer = false)),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.SHOW_TOTAL_TIMER_TOGGLE).performScrollTo().assertIsOn()
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.SHOW_QUESTION_TIMER_TOGGLE).assertIsOff().performClick()

        assertThat(actions.lastArgumentOf("onShowQuestionTimerChange")).isEqualTo(true)
        assertThat(actions.calls).doesNotContain("onShowTotalTimerChange")
    }

    @Test
    fun reviewOrderSubtitle_explainsTheSelectedOption() {
        setContent(uiState = SettingsUiState(app = AppSettings(reviewPriority = ReviewPriority.RANK_UP)))

        composeTestRule.onNodeWithText("below Guru come first", substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("random order", substring = true).assertDoesNotExist()
    }

    @Test
    fun holdingAStepperButton_repeatsTheStep() {
        val actions = RecordingSettingsActions()
        setContent(uiState = SettingsUiState(app = AppSettings(dailyLessonGoal = 15)), actions = actions)

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.LESSON_GOAL_INCREASE).performTouchInput { down(center) }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.LESSON_GOAL_INCREASE).performTouchInput { up() }

        assertThat(actions.calls.count { it == "onDailyLessonGoalChange" }).isGreaterThan(1)
    }

    @Test
    fun tappingAStepperButton_stepsExactlyOnce() {
        val actions = RecordingSettingsActions()
        setContent(uiState = SettingsUiState(app = AppSettings(dailyLessonGoal = 15)), actions = actions)

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.LESSON_GOAL_INCREASE).performClick()

        assertThat(actions.calls.count { it == "onDailyLessonGoalChange" }).isEqualTo(1)
    }

    @Test
    fun friendsRow_opensLeaderboard() {
        var opened = false
        setContent(uiState = SettingsUiState(), onOpenLeaderboard = { opened = true })

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.FRIENDS_ROW).performScrollTo().performClick()
        assert(opened)
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
            uiState = SettingsUiState(fullRefresh = FullRefreshStatus.InFlight),
            actions = actions
        )

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.FULL_REFRESH_PROGRESS).performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.FULL_REFRESH_ROW).performClick()
        assertThat(actions.calls).doesNotContain("onFullRefreshRequested")
    }

    @Test
    fun fullRefreshRow_showsErrorMessageOnFailure() {
        setContent(uiState = SettingsUiState(fullRefresh = FullRefreshStatus.Failed("WaniKani API error (500)")))

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.FULL_REFRESH_ERROR_TEXT).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun accountRow_showsUsernameAndLevel() {
        setContent(uiState = SettingsUiState(account = AccountSummary(username = "koichi", level = 12)))

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.ACCOUNT_ROW).performScrollTo()
        composeTestRule.onNodeWithText("koichi").assertIsDisplayed()
        composeTestRule.onNodeWithText("Level 12", substring = true).assertIsDisplayed()
    }

    @Test
    fun accountRow_isHiddenBeforeTheFirstSync_butLogOutIsStillOffered() {
        setContent(uiState = SettingsUiState(account = null))

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.ACCOUNT_ROW).assertDoesNotExist()
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.LOG_OUT_ROW).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun logOutRow_asksForConfirmationBeforeLoggingOut() {
        val actions = RecordingSettingsActions()
        setContent(uiState = SettingsUiState(), actions = actions)

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.LOG_OUT_ROW).performScrollTo().performClick()
        assertThat(actions.calls).doesNotContain("onLogOutRequested")

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.LOG_OUT_CONFIRM_BUTTON).performClick()
        assertThat(actions.calls).contains("onLogOutRequested")
    }

    @Test
    fun cancellingTheLogOutConfirmation_staysLoggedIn() {
        val actions = RecordingSettingsActions()
        setContent(uiState = SettingsUiState(), actions = actions)

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.LOG_OUT_ROW).performScrollTo().performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.LOG_OUT_CONFIRM_BUTTON).assertDoesNotExist()
        assertThat(actions.calls).doesNotContain("onLogOutRequested")
    }

    @Test
    fun footer_showsVersionNameAndBuild() {
        setContent(uiState = SettingsUiState(), appVersion = AppVersion(name = "1.13", build = "14"))

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.APP_VERSION_TEXT)
            .performScrollTo()
            .assertTextEquals("Version 1.13 (14)")
    }

    @Test
    fun footer_omitsVersionWhenThePlatformDoesNotReportOne() {
        setContent(uiState = SettingsUiState(), appVersion = null)

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.APP_VERSION_TEXT).assertDoesNotExist()
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
    override fun onDailyStudyMinutesGoalChange(minutes: Int) = record("onDailyStudyMinutesGoalChange", minutes)
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
    override fun onRequireTapToRevealMeaningAnswerChange(enabled: Boolean) = record("onRequireTapToRevealMeaningAnswerChange", enabled)
    override fun onRequireTapToRevealReadingAnswerChange(enabled: Boolean) = record("onRequireTapToRevealReadingAnswerChange", enabled)
    override fun onShowAnswerReadingPitchAccentChange(enabled: Boolean) = record("onShowAnswerReadingPitchAccentChange", enabled)
    override fun onHideContextSentenceTranslationsChange(enabled: Boolean) = record("onHideContextSentenceTranslationsChange", enabled)
    override fun onReviewPriorityChange(priority: ReviewPriority) = record("onReviewPriorityChange", priority)
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
    override fun onLogOutRequested(): Job {
        record("onLogOutRequested")
        return Job()
    }
}
