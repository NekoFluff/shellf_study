package com.crazyfluff.shellfstudy.shared.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crazyfluff.shellfstudy.shared.data.BACKLOG_THRESHOLD_RANGE
import com.crazyfluff.shellfstudy.shared.data.DAILY_LESSON_GOAL_RANGE
import com.crazyfluff.shellfstudy.shared.data.DAILY_STUDY_MINUTES_GOAL_RANGE
import com.crazyfluff.shellfstudy.shared.data.DAILY_STUDY_MINUTES_GOAL_STEP
import com.crazyfluff.shellfstudy.shared.data.LESSON_BATCH_SIZE_RANGE
import com.crazyfluff.shellfstudy.shared.data.NotificationSettings
import com.crazyfluff.shellfstudy.shared.data.ThemeMode
import com.crazyfluff.shellfstudy.shared.data.model.ReviewPriority
import com.crazyfluff.shellfstudy.shared.designsystem.AppVersion
import com.crazyfluff.shellfstudy.shared.designsystem.components.ListGroup
import com.crazyfluff.shellfstudy.shared.designsystem.dialog.ConfirmationDialog
import com.crazyfluff.shellfstudy.shared.designsystem.rememberAppVersion
import com.crazyfluff.shellfstudy.shared.designsystem.rememberPermissionRequest
import com.crazyfluff.shellfstudy.shared.designsystem.theme.einkBorder
import com.crazyfluff.shellfstudy.shared.designsystem.theme.emphasisContainerColor
import com.crazyfluff.shellfstudy.shared.designsystem.time.formatHour
import com.crazyfluff.shellfstudy.shared.designsystem.time.rememberIs24HourClock
import org.koin.compose.viewmodel.koinViewModel

object SettingsScreenTestTags {
    const val BACK_BUTTON = "settings_back_button"
    const val LESSON_GOAL_DECREASE = "settings_lesson_goal_decrease"
    const val LESSON_GOAL_INCREASE = "settings_lesson_goal_increase"
    const val LESSON_GOAL_VALUE = "settings_lesson_goal_value"
    const val STUDY_GOAL_DECREASE = "settings_study_goal_decrease"
    const val STUDY_GOAL_INCREASE = "settings_study_goal_increase"
    const val STUDY_GOAL_VALUE = "settings_study_goal_value"
    const val LESSON_BATCH_SIZE_DECREASE = "settings_lesson_batch_size_decrease"
    const val LESSON_BATCH_SIZE_INCREASE = "settings_lesson_batch_size_increase"
    const val LESSON_BATCH_SIZE_VALUE = "settings_lesson_batch_size_value"
    const val THEME_SYSTEM_OPTION = "settings_theme_system_option"
    const val THEME_LIGHT_OPTION = "settings_theme_light_option"
    const val THEME_DARK_OPTION = "settings_theme_dark_option"
    const val THEME_EINK_OPTION = "settings_theme_eink_option"
    const val PITCH_ACCENT_TOGGLE = "settings_pitch_accent_toggle"
    const val AUTOPLAY_AUDIO_TOGGLE = "settings_autoplay_audio_toggle"
    const val MP3_ONLY_AUDIO_TOGGLE = "settings_mp3_only_audio_toggle"
    const val SHOW_SUBJECT_TYPE_LABEL_TOGGLE = "settings_show_subject_type_label_toggle"
    const val SHOW_TOTAL_TIMER_TOGGLE = "settings_show_total_timer_toggle"
    const val SHOW_QUESTION_TIMER_TOGGLE = "settings_show_question_timer_toggle"
    const val STROKE_ORDER_TOGGLE = "settings_stroke_order_toggle"
    const val JAPANESE_KEYBOARD_TOGGLE = "settings_use_japanese_keyboard_toggle"
    const val CLOSE_ENOUGH_ANSWERS_TOGGLE = "settings_close_enough_answers_toggle"
    const val REQUIRE_TAP_TO_REVEAL_MEANING_ANSWER_TOGGLE = "settings_require_tap_to_reveal_meaning_answer_toggle"
    const val REQUIRE_TAP_TO_REVEAL_READING_ANSWER_TOGGLE = "settings_require_tap_to_reveal_reading_answer_toggle"
    const val ANSWER_READING_PITCH_ACCENT_TOGGLE = "settings_answer_reading_pitch_accent_toggle"
    fun reviewPriorityOptionTag(priority: ReviewPriority) = "settings_review_priority_${priority.name.lowercase()}_option"
    const val HIDE_CONTEXT_SENTENCE_TRANSLATIONS_TOGGLE = "settings_hide_context_sentence_translations_toggle"
    const val NOTIFICATIONS_MASTER_TOGGLE = "settings_notifications_master_toggle"
    const val REVIEWS_AVAILABLE_TOGGLE = "settings_reviews_available_toggle"
    const val REVIEWS_BACKLOG_TOGGLE = "settings_reviews_backlog_toggle"
    const val BACKLOG_THRESHOLD_DECREASE = "settings_backlog_threshold_decrease"
    const val BACKLOG_THRESHOLD_INCREASE = "settings_backlog_threshold_increase"
    const val BACKLOG_THRESHOLD_VALUE = "settings_backlog_threshold_value"
    const val DAILY_REMINDER_TOGGLE = "settings_daily_reminder_toggle"
    const val DAILY_REMINDER_TIME_ROW = "settings_daily_reminder_time_row"
    const val QUIET_HOURS_TOGGLE = "settings_quiet_hours_toggle"
    const val QUIET_HOURS_START_ROW = "settings_quiet_hours_start_row"
    const val QUIET_HOURS_END_ROW = "settings_quiet_hours_end_row"
    fun hourOptionTag(hour: Int) = "settings_hour_option_$hour"
    const val FRIENDS_ROW = "settings_friends_row"
    const val FULL_REFRESH_ROW = "settings_full_refresh_row"
    const val FULL_REFRESH_PROGRESS = "settings_full_refresh_progress"
    const val FULL_REFRESH_CONFIRM_BUTTON = "settings_full_refresh_confirm_button"
    const val FULL_REFRESH_ERROR_TEXT = "settings_full_refresh_error_text"
    const val ACCOUNT_ROW = "settings_account_row"
    const val LOG_OUT_ROW = "settings_log_out_row"
    const val LOG_OUT_CONFIRM_BUTTON = "settings_log_out_confirm_button"
    const val APP_VERSION_TEXT = "settings_app_version_text"

    /** The three tags each [StepperRow] addresses, grouped once so every call site passes one value
     *  instead of three strings that can only ever travel together. */
    val LESSON_GOAL_TAGS = StepperRowTestTags(LESSON_GOAL_DECREASE, LESSON_GOAL_INCREASE, LESSON_GOAL_VALUE)
    val STUDY_GOAL_TAGS = StepperRowTestTags(STUDY_GOAL_DECREASE, STUDY_GOAL_INCREASE, STUDY_GOAL_VALUE)
    val LESSON_BATCH_SIZE_TAGS =
        StepperRowTestTags(LESSON_BATCH_SIZE_DECREASE, LESSON_BATCH_SIZE_INCREASE, LESSON_BATCH_SIZE_VALUE)
    val BACKLOG_THRESHOLD_TAGS =
        StepperRowTestTags(BACKLOG_THRESHOLD_DECREASE, BACKLOG_THRESHOLD_INCREASE, BACKLOG_THRESHOLD_VALUE)
}

/** [StepperRow]'s three nodes: the two buttons and the value between them. */
data class StepperRowTestTags(
    val decrease: String,
    val increase: String,
    val value: String
)

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenLeaderboard: () -> Unit = {},
    onLoggedOut: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) onLoggedOut()
    }

    val requestNotificationPermission = rememberPermissionRequest { granted ->
        viewModel.onNotificationsPermissionResult(granted)
    }

    SettingsScreen(
        uiState = uiState,
        actions = viewModel,
        onNotificationsEnabledChange = { enabled ->
            if (enabled) requestNotificationPermission() else viewModel.onNotificationsEnabledChange(false)
        },
        onOpenLeaderboard = onOpenLeaderboard,
        onBack = onBack,
        appVersion = rememberAppVersion()
    )
}

/**
 * One scrolling page. At the top is a Daily plan card for the two numbers people change most. Below
 * it, the settings are grouped by where they take effect: building a session, answering, the quiz
 * screen, audio, subject pages, then the app itself. The account and log out come last, as the one
 * destructive action, and the version sits in a quiet footer under everything.
 *
 * It's a plain scrolling [Column], not a lazy list. The page is short enough that laziness buys
 * nothing, and it keeps every row reachable by `performScrollTo` in tests.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    actions: SettingsActions,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    onOpenLeaderboard: () -> Unit = {},
    onBack: () -> Unit,
    appVersion: AppVersion? = null
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(SettingsScreenTestTags.BACK_BUTTON)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 16.dp)
        ) {
            DailyPlanCard(uiState, actions)
            StudySessionsGroup(uiState, actions)
            AnsweringGroup(uiState, actions)
            QuizScreenGroup(uiState, actions)
            AudioGroup(uiState, actions)
            SubjectPagesGroup(uiState, actions)
            AppearanceGroup(uiState, actions)
            NotificationsGroup(uiState, actions, onNotificationsEnabledChange)
            FriendsGroup(onOpenLeaderboard)
            DataGroup(uiState, actions)
            AccountGroup(uiState, actions)
            CreditsGroup()
            AppFooter(appVersion)
        }
    }
}

/** The daily targets the dashboard measures you against. They're first because they're the settings
 *  people come back to most. */
@Composable
private fun DailyPlanCard(uiState: SettingsUiState, actions: SettingsActions) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = emphasisContainerColor(),
        border = einkBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                text = "Daily plan",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                text = "Your targets on the dashboard",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            StepperRow(
                title = "New lessons",
                value = uiState.app.dailyLessonGoal,
                onValueChange = actions::onDailyLessonGoalChange,
                testTags = SettingsScreenTestTags.LESSON_GOAL_TAGS,
                range = DAILY_LESSON_GOAL_RANGE
            )
            StepperRow(
                title = "Study time",
                value = uiState.app.dailyStudyMinutesGoal,
                onValueChange = actions::onDailyStudyMinutesGoalChange,
                testTags = SettingsScreenTestTags.STUDY_GOAL_TAGS,
                range = DAILY_STUDY_MINUTES_GOAL_RANGE,
                step = DAILY_STUDY_MINUTES_GOAL_STEP,
                valueLabel = { "$it min" }
            )
        }
    }
}

private fun reviewPriorityExplanation(priority: ReviewPriority): String = when (priority) {
    ReviewPriority.DEFAULT -> "Due reviews come up in random order. It never changes when an item is due."
    ReviewPriority.RANK_UP ->
        "This level's radicals and kanji below Guru come earlier in the queue when available, " +
            "so you level up sooner."
}

/** How a lesson or review session is put together. */
@Composable
private fun StudySessionsGroup(uiState: SettingsUiState, actions: SettingsActions) {
    ListGroup(title = "Study sessions") {
        val batchSize = uiState.app.lessonBatchSize
        StepperRow(
            title = "Lesson batch size",
            subtitle = "Learn, then quiz, in rounds of $batchSize",
            value = batchSize,
            onValueChange = actions::onLessonBatchSizeChange,
            testTags = SettingsScreenTestTags.LESSON_BATCH_SIZE_TAGS,
            range = LESSON_BATCH_SIZE_RANGE
        )
        SingleChoiceRow(
            title = "Review order",
            subtitle = reviewPriorityExplanation(uiState.app.reviewPriority),
            options = ReviewPriority.entries.map {
                ChoiceOption(it, it.label, SettingsScreenTestTags.reviewPriorityOptionTag(it))
            },
            selected = uiState.app.reviewPriority,
            onSelect = actions::onReviewPriorityChange
        )
    }
}

/** How answers are typed and graded. */
@Composable
private fun AnsweringGroup(uiState: SettingsUiState, actions: SettingsActions) {
    ListGroup(title = "Answering") {
        SwitchRow(
            title = "Use my Japanese keyboard",
            subtitle = "For readings, instead of the built-in romaji → kana converter",
            checked = uiState.app.useJapaneseKeyboard,
            onCheckedChange = actions::onUseJapaneseKeyboardChange,
            testTag = SettingsScreenTestTags.JAPANESE_KEYBOARD_TOGGLE
        )
        SwitchRow(
            title = "Forgive small typos",
            subtitle = "Meanings a letter or two off still count",
            checked = uiState.app.closeEnoughAnswersEnabled,
            onCheckedChange = actions::onCloseEnoughAnswersEnabledChange,
            testTag = SettingsScreenTestTags.CLOSE_ENOUGH_ANSWERS_TOGGLE
        )
        MultiChoiceRow(
            title = "Hold back the correct answer",
            subtitle = "After a mistake, tap to reveal it, so you can keep thinking",
            options = listOf(
                ToggleOption(
                    label = "Meaning",
                    checked = uiState.app.requireTapToRevealMeaningAnswer,
                    onCheckedChange = actions::onRequireTapToRevealMeaningAnswerChange,
                    testTag = SettingsScreenTestTags.REQUIRE_TAP_TO_REVEAL_MEANING_ANSWER_TOGGLE
                ),
                ToggleOption(
                    label = "Reading",
                    checked = uiState.app.requireTapToRevealReadingAnswer,
                    onCheckedChange = actions::onRequireTapToRevealReadingAnswerChange,
                    testTag = SettingsScreenTestTags.REQUIRE_TAP_TO_REVEAL_READING_ANSWER_TOGGLE
                )
            )
        )
    }
}

/** What the lesson-quiz and review screens show. */
@Composable
private fun QuizScreenGroup(uiState: SettingsUiState, actions: SettingsActions) {
    ListGroup(title = "Quiz screen") {
        SwitchRow(
            title = "Show item type",
            subtitle = "Radical, Kanji or Vocabulary under the word",
            checked = uiState.app.showSubjectTypeLabel,
            onCheckedChange = actions::onShowSubjectTypeLabelChange,
            testTag = SettingsScreenTestTags.SHOW_SUBJECT_TYPE_LABEL_TOGGLE
        )
        SwitchRow(
            title = "Show reading & pitch after answering",
            subtitle = "Above the word, after a reading question",
            checked = uiState.app.showAnswerReadingPitchAccent,
            onCheckedChange = actions::onShowAnswerReadingPitchAccentChange,
            testTag = SettingsScreenTestTags.ANSWER_READING_PITCH_ACCENT_TOGGLE
        )
        MultiChoiceRow(
            title = "Timers",
            subtitle = "Time spent on the whole session, and on the current question",
            options = listOf(
                ToggleOption(
                    label = "Session",
                    checked = uiState.app.showTotalTimer,
                    onCheckedChange = actions::onShowTotalTimerChange,
                    testTag = SettingsScreenTestTags.SHOW_TOTAL_TIMER_TOGGLE
                ),
                ToggleOption(
                    label = "Question",
                    checked = uiState.app.showQuestionTimer,
                    onCheckedChange = actions::onShowQuestionTimerChange,
                    testTag = SettingsScreenTestTags.SHOW_QUESTION_TIMER_TOGGLE
                )
            )
        )
    }
}

@Composable
private fun AudioGroup(uiState: SettingsUiState, actions: SettingsActions) {
    ListGroup(title = "Audio") {
        SwitchRow(
            title = "Auto-play pronunciation",
            subtitle = "When a reading answer is revealed",
            checked = uiState.app.autoplayPronunciationAudio,
            onCheckedChange = actions::onAutoplayPronunciationAudioChange,
            testTag = SettingsScreenTestTags.AUTOPLAY_AUDIO_TOGGLE
        )
        SwitchRow(
            title = "MP3 audio only",
            subtitle = "For devices that can't play Ogg, like e-ink readers",
            checked = uiState.app.restrictAudioToMp3,
            onCheckedChange = actions::onRestrictAudioToMp3Change,
            testTag = SettingsScreenTestTags.MP3_ONLY_AUDIO_TOGGLE
        )
    }
}

/** What the subject detail pages and lesson pages include. */
@Composable
private fun SubjectPagesGroup(uiState: SettingsUiState, actions: SettingsActions) {
    ListGroup(title = "Subject pages") {
        SwitchRow(
            title = "Pitch-accent markers",
            subtitle = "On vocabulary readings",
            checked = uiState.app.showPitchAccent,
            onCheckedChange = actions::onShowPitchAccentChange,
            testTag = SettingsScreenTestTags.PITCH_ACCENT_TOGGLE
        )
        SwitchRow(
            title = "Stroke order",
            subtitle = "Animated diagram and writing practice for kanji",
            checked = uiState.app.showStrokeOrder,
            onCheckedChange = actions::onShowStrokeOrderChange,
            testTag = SettingsScreenTestTags.STROKE_ORDER_TOGGLE
        )
        SwitchRow(
            title = "Hide sentence translations",
            subtitle = "Read the Japanese first, then tap to reveal",
            checked = uiState.app.hideContextSentenceTranslations,
            onCheckedChange = actions::onHideContextSentenceTranslationsChange,
            testTag = SettingsScreenTestTags.HIDE_CONTEXT_SENTENCE_TRANSLATIONS_TOGGLE
        )
    }
}

private val THEME_OPTIONS = listOf(
    ChoiceOption(ThemeMode.SYSTEM, "System", SettingsScreenTestTags.THEME_SYSTEM_OPTION),
    ChoiceOption(ThemeMode.LIGHT, "Light", SettingsScreenTestTags.THEME_LIGHT_OPTION),
    ChoiceOption(ThemeMode.DARK, "Dark", SettingsScreenTestTags.THEME_DARK_OPTION),
    ChoiceOption(ThemeMode.EINK, "E-ink", SettingsScreenTestTags.THEME_EINK_OPTION)
)

@Composable
private fun AppearanceGroup(uiState: SettingsUiState, actions: SettingsActions) {
    ListGroup(title = "Appearance") {
        SingleChoiceRow(
            title = "Theme",
            subtitle = if (uiState.app.themeMode == ThemeMode.EINK) "Grayscale, high contrast, no shadows" else null,
            options = THEME_OPTIONS,
            selected = uiState.app.themeMode,
            onSelect = actions::onThemeModeChange
        )
    }
}

/** The backlog threshold moves in fives: a single review either way isn't a meaningful change. */
private const val BACKLOG_THRESHOLD_STEP = 5

/** Which hour setting the picker dialog is editing. Only one can be open at a time. */
private enum class HourSetting(val title: String) {
    DAILY_REMINDER("Reminder time"),
    QUIET_START("Quiet hours start"),
    QUIET_END("Quiet hours end");

    fun currentHour(notifications: NotificationSettings): Int = when (this) {
        DAILY_REMINDER -> notifications.dailyReminderHour
        QUIET_START -> notifications.quietHoursStartHour
        QUIET_END -> notifications.quietHoursEndHour
    }

    fun save(actions: SettingsActions, hour: Int) {
        when (this) {
            DAILY_REMINDER -> actions.onDailyReminderHourChange(hour)
            QUIET_START -> actions.onQuietHoursStartHourChange(hour)
            QUIET_END -> actions.onQuietHoursEndHourChange(hour)
        }
    }
}

/**
 * The notifications settings. The main switch gates every row below it.
 *
 * While it's off, those rows stay visible but disabled, so you can see what turning it on offers and
 * the page doesn't jump. A setting that belongs to one row (the backlog threshold, the reminder
 * time, the quiet window) only appears while that row is on.
 */
@Composable
private fun NotificationsGroup(
    uiState: SettingsUiState,
    actions: SettingsActions,
    onNotificationsEnabledChange: (Boolean) -> Unit
) {
    val notifications = uiState.notifications
    val is24h = rememberIs24HourClock()
    var editing by remember { mutableStateOf<HourSetting?>(null) }

    ListGroup(title = "Notifications") {
        MainSwitchRow(
            title = "Allow notifications",
            checked = notifications.notificationsEnabled,
            onCheckedChange = onNotificationsEnabledChange,
            testTag = SettingsScreenTestTags.NOTIFICATIONS_MASTER_TOGGLE
        )
        ReviewAlertRows(notifications, actions)
        DailyReminderRows(notifications, actions, is24h, onEditHour = { editing = it })
        QuietHoursRows(notifications, actions, is24h, onEditHour = { editing = it })
    }

    editing?.let { setting ->
        HourPickerDialog(
            title = setting.title,
            selectedHour = setting.currentHour(notifications),
            is24h = is24h,
            onSelect = { hour ->
                editing = null
                setting.save(actions, hour)
            },
            onDismiss = { editing = null }
        )
    }
}

/** "Reviews ready" and the backlog warning, with the warning's threshold under it. */
@Composable
private fun ReviewAlertRows(notifications: NotificationSettings, actions: SettingsActions) {
    val enabled = notifications.notificationsEnabled
    SwitchRow(
        title = "Reviews ready",
        subtitle = "As soon as new reviews unlock",
        checked = notifications.reviewsAvailableEnabled,
        onCheckedChange = actions::onReviewsAvailableEnabledChange,
        testTag = SettingsScreenTestTags.REVIEWS_AVAILABLE_TOGGLE,
        enabled = enabled
    )
    SwitchRow(
        title = "Backlog warning",
        subtitle = "When reviews pile up · at most every 6 hours",
        checked = notifications.reviewsBacklogEnabled,
        onCheckedChange = actions::onReviewsBacklogEnabledChange,
        testTag = SettingsScreenTestTags.REVIEWS_BACKLOG_TOGGLE,
        enabled = enabled
    )
    if (enabled && notifications.reviewsBacklogEnabled) {
        StepperRow(
            title = "Warn above",
            value = notifications.backlogThreshold,
            onValueChange = actions::onBacklogThresholdChange,
            testTags = SettingsScreenTestTags.BACKLOG_THRESHOLD_TAGS,
            range = BACKLOG_THRESHOLD_RANGE,
            step = BACKLOG_THRESHOLD_STEP,
            indent = true
        )
    }
}

@Composable
private fun DailyReminderRows(
    notifications: NotificationSettings,
    actions: SettingsActions,
    is24h: Boolean,
    onEditHour: (HourSetting) -> Unit
) {
    val enabled = notifications.notificationsEnabled
    SwitchRow(
        title = "Daily reminder",
        subtitle = "Only if you haven't studied yet that day",
        checked = notifications.dailyReminderEnabled,
        onCheckedChange = actions::onDailyReminderEnabledChange,
        testTag = SettingsScreenTestTags.DAILY_REMINDER_TOGGLE,
        enabled = enabled
    )
    if (enabled && notifications.dailyReminderEnabled) {
        NavRow(
            title = "Time",
            value = formatHour(notifications.dailyReminderHour, is24h),
            onClick = { onEditHour(HourSetting.DAILY_REMINDER) },
            testTag = SettingsScreenTestTags.DAILY_REMINDER_TIME_ROW,
            indent = true
        )
    }
}

@Composable
private fun QuietHoursRows(
    notifications: NotificationSettings,
    actions: SettingsActions,
    is24h: Boolean,
    onEditHour: (HourSetting) -> Unit
) {
    val enabled = notifications.notificationsEnabled
    SwitchRow(
        title = "Quiet hours",
        subtitle = "Alerts wait until the window ends. The daily reminder is skipped",
        checked = notifications.quietHoursEnabled,
        onCheckedChange = actions::onQuietHoursEnabledChange,
        testTag = SettingsScreenTestTags.QUIET_HOURS_TOGGLE,
        enabled = enabled
    )
    if (enabled && notifications.quietHoursEnabled) {
        NavRow(
            title = "From",
            value = formatHour(notifications.quietHoursStartHour, is24h),
            onClick = { onEditHour(HourSetting.QUIET_START) },
            testTag = SettingsScreenTestTags.QUIET_HOURS_START_ROW,
            indent = true
        )
        NavRow(
            title = "Until",
            value = formatHour(notifications.quietHoursEndHour, is24h),
            onClick = { onEditHour(HourSetting.QUIET_END) },
            testTag = SettingsScreenTestTags.QUIET_HOURS_END_ROW,
            indent = true
        )
    }
}

@Composable
private fun FriendsGroup(onOpenLeaderboard: () -> Unit) {
    ListGroup(title = "Friends") {
        NavRow(
            title = "Friends",
            subtitle = "Add friends and see their progress",
            onClick = onOpenLeaderboard,
            testTag = SettingsScreenTestTags.FRIENDS_ROW
        )
    }
}

@Composable
private fun DataGroup(uiState: SettingsUiState, actions: SettingsActions) {
    var showFullRefreshConfirm by remember { mutableStateOf(false) }
    ListGroup(title = "Data") {
        NavRow(
            title = "Re-download all data",
            subtitle = "Fixes content that looks wrong or missing",
            onClick = { showFullRefreshConfirm = true },
            enabled = !uiState.isFullRefreshing,
            testTag = SettingsScreenTestTags.FULL_REFRESH_ROW,
            trailing = if (uiState.isFullRefreshing) {
                {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp).testTag(SettingsScreenTestTags.FULL_REFRESH_PROGRESS)
                    )
                }
            } else {
                null
            }
        )
        val fullRefreshError = uiState.fullRefreshError
        if (fullRefreshError != null) {
            Text(
                text = fullRefreshError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    .testTag(SettingsScreenTestTags.FULL_REFRESH_ERROR_TEXT)
            )
        }
    }

    if (showFullRefreshConfirm) {
        ConfirmationDialog(
            title = "Re-download all data?",
            text = "This re-downloads your entire WaniKani library from scratch instead of just what's changed. It may take longer than a normal sync and use more data.",
            confirmLabel = "Re-download",
            onConfirm = {
                showFullRefreshConfirm = false
                actions.onFullRefreshRequested()
            },
            onDismiss = { showFullRefreshConfirm = false },
            confirmButtonTestTag = SettingsScreenTestTags.FULL_REFRESH_CONFIRM_BUTTON
        )
    }
}

private data class ThirdPartyCredit(val source: String, val usedFor: String, val license: String)

private val THIRD_PARTY_CREDITS = listOf(
    ThirdPartyCredit(source = "KanjiVG", usedFor = "Kanji stroke-order diagrams", license = "CC BY-SA 3.0"),
    ThirdPartyCredit(
        source = "Noto Sans JP",
        usedFor = "Japanese text rendering",
        license = "SIL Open Font License 1.1"
    ),
    ThirdPartyCredit(
        source = "Kanjium",
        usedFor = "Pitch-accent dictionary (additions by Uros O.)",
        license = "CC BY-SA 4.0"
    )
)

/** Attribution for the third-party data bundled with the app. The CC BY-SA licenses covering the
 *  KanjiVG and Kanjium data require it, which is why it stays on the page instead of behind a tap. */
@Composable
private fun CreditsGroup() {
    ListGroup(title = "Open source & data credits") {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            THIRD_PARTY_CREDITS.forEach { credit ->
                Column {
                    Text("${credit.source} · ${credit.license}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = credit.usedFor,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Who's signed in, and log out. The account row is only shown once a sync has told us the
 *  username; log out is always there. */
@Composable
private fun AccountGroup(uiState: SettingsUiState, actions: SettingsActions) {
    var showLogOutConfirm by remember { mutableStateOf(false) }
    ListGroup(title = "Account") {
        val account = uiState.account
        if (account != null) {
            InfoRow(
                title = account.username,
                subtitle = "Level ${account.level} · Signed in with your API token",
                testTag = SettingsScreenTestTags.ACCOUNT_ROW
            )
        }
        DestructiveRow(
            title = "Log out",
            icon = Icons.AutoMirrored.Filled.Logout,
            onClick = { showLogOutConfirm = true },
            testTag = SettingsScreenTestTags.LOG_OUT_ROW
        )
    }

    if (showLogOutConfirm) {
        ConfirmationDialog(
            title = "Log out?",
            text = "You'll need your WaniKani API token to sign back in. Your study time and streak " +
                "stay on this device and come back when you sign in with the same account.",
            confirmLabel = "Log out",
            onConfirm = {
                showLogOutConfirm = false
                actions.onLogOutRequested()
            },
            onDismiss = { showLogOutConfirm = false },
            confirmButtonTestTag = SettingsScreenTestTags.LOG_OUT_CONFIRM_BUTTON
        )
    }
}

/** The app name and version, centred under the last group. The version is something people
 *  only look up when reporting a bug, so it sits here rather than in a group of its own. */
@Composable
private fun AppFooter(appVersion: AppVersion?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Text("Shellf Study", style = MaterialTheme.typography.titleSmall)
        if (appVersion != null) {
            Text(
                text = "Version ${appVersion.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(SettingsScreenTestTags.APP_VERSION_TEXT)
            )
        }
        Text(
            text = "Unofficial app · not affiliated with Tofugu LLC",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
