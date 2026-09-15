package com.crazyfluff.shellfstudy.shared.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.crazyfluff.shellfstudy.shared.designsystem.dialog.ConfirmationDialog
import com.crazyfluff.shellfstudy.shared.designsystem.rememberPermissionRequest
import com.crazyfluff.shellfstudy.shared.data.BACKLOG_THRESHOLD_RANGE
import com.crazyfluff.shellfstudy.shared.data.DAILY_LESSON_GOAL_RANGE
import com.crazyfluff.shellfstudy.shared.data.LESSON_BATCH_SIZE_RANGE
import com.crazyfluff.shellfstudy.shared.data.ThemeMode

object SettingsScreenTestTags {
    const val BACK_BUTTON = "settings_back_button"
    const val LESSON_GOAL_DECREASE = "settings_lesson_goal_decrease"
    const val LESSON_GOAL_INCREASE = "settings_lesson_goal_increase"
    const val LESSON_GOAL_VALUE = "settings_lesson_goal_value"
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
    const val HIDE_CONTEXT_SENTENCE_TRANSLATIONS_TOGGLE = "settings_hide_context_sentence_translations_toggle"
    const val NOTIFICATIONS_MASTER_TOGGLE = "settings_notifications_master_toggle"
    const val REVIEWS_AVAILABLE_TOGGLE = "settings_reviews_available_toggle"
    const val REVIEWS_BACKLOG_TOGGLE = "settings_reviews_backlog_toggle"
    const val BACKLOG_THRESHOLD_DECREASE = "settings_backlog_threshold_decrease"
    const val BACKLOG_THRESHOLD_INCREASE = "settings_backlog_threshold_increase"
    const val BACKLOG_THRESHOLD_VALUE = "settings_backlog_threshold_value"
    const val DAILY_REMINDER_TOGGLE = "settings_daily_reminder_toggle"
    const val DAILY_REMINDER_HOUR_DECREASE = "settings_daily_reminder_hour_decrease"
    const val DAILY_REMINDER_HOUR_INCREASE = "settings_daily_reminder_hour_increase"
    const val DAILY_REMINDER_HOUR_VALUE = "settings_daily_reminder_hour_value"
    const val QUIET_HOURS_TOGGLE = "settings_quiet_hours_toggle"
    const val QUIET_HOURS_START_DECREASE = "settings_quiet_hours_start_decrease"
    const val QUIET_HOURS_START_INCREASE = "settings_quiet_hours_start_increase"
    const val QUIET_HOURS_START_VALUE = "settings_quiet_hours_start_value"
    const val QUIET_HOURS_END_DECREASE = "settings_quiet_hours_end_decrease"
    const val QUIET_HOURS_END_INCREASE = "settings_quiet_hours_end_increase"
    const val QUIET_HOURS_END_VALUE = "settings_quiet_hours_end_value"
    const val FULL_REFRESH_ROW = "settings_full_refresh_row"
    const val FULL_REFRESH_PROGRESS = "settings_full_refresh_progress"
    const val FULL_REFRESH_CONFIRM_BUTTON = "settings_full_refresh_confirm_button"
    const val FULL_REFRESH_ERROR_TEXT = "settings_full_refresh_error_text"

    /** The three tags each [StepperRow] addresses, grouped once so every call site passes one value
     *  instead of three strings that can only ever travel together. */
    val BACKLOG_THRESHOLD_TAGS = StepperRowTestTags(BACKLOG_THRESHOLD_DECREASE, BACKLOG_THRESHOLD_INCREASE, BACKLOG_THRESHOLD_VALUE)
    val DAILY_REMINDER_HOUR_TAGS = StepperRowTestTags(DAILY_REMINDER_HOUR_DECREASE, DAILY_REMINDER_HOUR_INCREASE, DAILY_REMINDER_HOUR_VALUE)
    val QUIET_HOURS_START_TAGS = StepperRowTestTags(QUIET_HOURS_START_DECREASE, QUIET_HOURS_START_INCREASE, QUIET_HOURS_START_VALUE)
    val QUIET_HOURS_END_TAGS = StepperRowTestTags(QUIET_HOURS_END_DECREASE, QUIET_HOURS_END_INCREASE, QUIET_HOURS_END_VALUE)
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
    viewModel: SettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

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
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    actions: SettingsActions,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    onOpenLeaderboard: () -> Unit = {},
    onBack: () -> Unit
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
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            DailyGoalSection(
                uiState = uiState,
                actions = actions,
            )

            Spacer(modifier = Modifier.height(16.dp))

            LessonBatchSizeSection(
                uiState = uiState,
                actions = actions,
            )

            Spacer(modifier = Modifier.height(16.dp))

            AppearanceSection(
                uiState = uiState,
                actions = actions,
            )

            Spacer(modifier = Modifier.height(16.dp))

            VocabularySection(
                uiState = uiState,
                actions = actions,
            )

            Spacer(modifier = Modifier.height(16.dp))

            ReviewsSection(
                uiState = uiState,
                actions = actions,
            )

            Spacer(modifier = Modifier.height(16.dp))

            NotificationsSection(
                uiState = uiState,
                actions = actions,
                onNotificationsEnabledChange = onNotificationsEnabledChange,
            )

            Spacer(modifier = Modifier.height(16.dp))

            FriendsSection(
                onOpenLeaderboard = onOpenLeaderboard,
            )

            Spacer(modifier = Modifier.height(16.dp))

            DataSection(
                uiState = uiState,
                actions = actions,
            )

            Spacer(modifier = Modifier.height(16.dp))

            ThirdPartyCreditsCard()
        }
    }

}

/** Card shell used for every settings section — a small tinted icon next to the section title, matching the dashboard's card language. */
@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

private data class ThirdPartyCredit(val source: String, val usedFor: String, val license: String)

private val THIRD_PARTY_CREDITS = listOf(
    ThirdPartyCredit(
        source = "KanjiVG",
        usedFor = "Kanji stroke-order diagrams",
        license = "CC BY-SA 3.0"
    ),
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

/** Static attribution list for bundled third-party data this app ships with — required by the
 *  CC BY-SA licenses covering the KanjiVG and Kanjium data files. Not a general licenses browser:
 *  just the small fixed set of sources bundled today. */
@Composable
private fun ThirdPartyCreditsCard(modifier: Modifier = Modifier) {
    SectionCard(title = "Open source & data credits", icon = Icons.Default.Info, modifier = modifier) {
        THIRD_PARTY_CREDITS.forEachIndexed { index, credit ->
            if (index > 0) Spacer(modifier = Modifier.height(8.dp))
            Column {
                Text(
                    text = "${credit.source} — ${credit.license}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = credit.usedFor,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Small caption splitting the Notifications card into Reviews/Reminders/Quiet hours groups. */
@Composable
private fun NotificationGroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    description: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.testTag(testTag))
        }
        if (description != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 52.dp)
            )
        }
    }
}

/** A +/- stepper, same shape as the daily-lesson-goal control above. [range] disables the relevant
 *  button once a step would go past it — left null (the default) for a cyclic value like an hour,
 *  which has no real min/max to disable against. */
@Composable
private fun StepperRow(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    testTags: StepperRowTestTags,
    step: Int = 1,
    range: IntRange? = null,
    valueLabel: (Int) -> String = { it.toString() }
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(label)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = { onValueChange(value - step) },
                enabled = range == null || value > range.first,
                modifier = Modifier.testTag(testTags.decrease)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease $label")
            }
            Text(
                text = valueLabel(value),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag(testTags.value)
            )
            IconButton(
                onClick = { onValueChange(value + step) },
                enabled = range == null || value < range.last,
                modifier = Modifier.testTag(testTags.increase)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase $label")
            }
        }
    }
}

private fun formatHour(hour: Int): String {
    val normalized = hour.mod(24)
    val hour12 = if (normalized % 12 == 0) 12 else normalized % 12
    val suffix = if (normalized < 12) "AM" else "PM"
    return "$hour12:00 $suffix"
}

@Composable
private fun ThemeOptionRow(
    label: String,
    mode: ThemeMode,
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    testTag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(mode) }
            .testTag(testTag)
            .padding(vertical = 8.dp)
    ) {
        RadioButton(selected = selected == mode, onClick = { onSelect(mode) })
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
    }
}

/** One settings section: the state it renders and the actions it offers. */
@Composable
private fun DailyGoalSection(
    uiState: SettingsUiState,
    actions: SettingsActions,
) {
    SectionCard(title = "Daily lesson goal", icon = Icons.AutoMirrored.Filled.MenuBook) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(
                onClick = { actions.onDailyLessonGoalChange(uiState.dailyLessonGoal - 1) },
                enabled = uiState.dailyLessonGoal > DAILY_LESSON_GOAL_RANGE.first,
                modifier = Modifier.testTag(SettingsScreenTestTags.LESSON_GOAL_DECREASE)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease daily lesson goal")
            }
            Text(
                text = uiState.dailyLessonGoal.toString(),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.testTag(SettingsScreenTestTags.LESSON_GOAL_VALUE)
            )
            IconButton(
                onClick = { actions.onDailyLessonGoalChange(uiState.dailyLessonGoal + 1) },
                enabled = uiState.dailyLessonGoal < DAILY_LESSON_GOAL_RANGE.last,
                modifier = Modifier.testTag(SettingsScreenTestTags.LESSON_GOAL_INCREASE)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase daily lesson goal")
            }
        }
    }
}

/** One settings section: the state it renders and the actions it offers. */
@Composable
private fun LessonBatchSizeSection(
    uiState: SettingsUiState,
    actions: SettingsActions,
) {
    SectionCard(title = "Lesson session size", icon = Icons.AutoMirrored.Filled.MenuBook) {
        Text(
            text = "New lessons are studied and quizzed in batches of this size, so a long session " +
                "becomes a series of short study→quiz cycles instead of one long one.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(
                onClick = { actions.onLessonBatchSizeChange(uiState.lessonBatchSize - 1) },
                enabled = uiState.lessonBatchSize > LESSON_BATCH_SIZE_RANGE.first,
                modifier = Modifier.testTag(SettingsScreenTestTags.LESSON_BATCH_SIZE_DECREASE)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Smaller batches")
            }
            Text(
                text = uiState.lessonBatchSize.toString(),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.testTag(SettingsScreenTestTags.LESSON_BATCH_SIZE_VALUE)
            )
            IconButton(
                onClick = { actions.onLessonBatchSizeChange(uiState.lessonBatchSize + 1) },
                enabled = uiState.lessonBatchSize < LESSON_BATCH_SIZE_RANGE.last,
                modifier = Modifier.testTag(SettingsScreenTestTags.LESSON_BATCH_SIZE_INCREASE)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Bigger batches")
            }
        }
    }
}

/** One settings section: the state it renders and the actions it offers. */
@Composable
private fun AppearanceSection(
    uiState: SettingsUiState,
    actions: SettingsActions,
) {
    SectionCard(title = "Appearance", icon = Icons.Default.Palette) {
        ThemeOptionRow(
            label = "System default",
            mode = ThemeMode.SYSTEM,
            selected = uiState.themeMode,
            onSelect = actions::onThemeModeChange,
            testTag = SettingsScreenTestTags.THEME_SYSTEM_OPTION
        )
        ThemeOptionRow(
            label = "Light",
            mode = ThemeMode.LIGHT,
            selected = uiState.themeMode,
            onSelect = actions::onThemeModeChange,
            testTag = SettingsScreenTestTags.THEME_LIGHT_OPTION
        )
        ThemeOptionRow(
            label = "Dark",
            mode = ThemeMode.DARK,
            selected = uiState.themeMode,
            onSelect = actions::onThemeModeChange,
            testTag = SettingsScreenTestTags.THEME_DARK_OPTION
        )
        ThemeOptionRow(
            label = "E-Ink (grayscale)",
            mode = ThemeMode.EINK,
            selected = uiState.themeMode,
            onSelect = actions::onThemeModeChange,
            testTag = SettingsScreenTestTags.THEME_EINK_OPTION
        )
    }
}

/** One settings section: the state it renders and the actions it offers. */
@Composable
private fun VocabularySection(
    uiState: SettingsUiState,
    actions: SettingsActions,
) {
    SectionCard(title = "Vocabulary", icon = Icons.Default.Translate) {
        ToggleRow(
            label = "Show pitch accent",
            description = "Overlays WaniKani's pitch-accent pattern markers on vocabulary readings.",
            checked = uiState.showPitchAccent,
            onCheckedChange = actions::onShowPitchAccentChange,
            testTag = SettingsScreenTestTags.PITCH_ACCENT_TOGGLE
        )
        ToggleRow(
            label = "Show stroke order",
            description = "Displays the animated stroke order diagram and writing practice canvas for kanji in the subject detail panel.",
            checked = uiState.showStrokeOrder,
            onCheckedChange = actions::onShowStrokeOrderChange,
            testTag = SettingsScreenTestTags.STROKE_ORDER_TOGGLE
        )
        ToggleRow(
            label = "Auto-play pronunciation audio",
            description = "Plays a word's audio automatically when a reading question is revealed during reviews.",
            checked = uiState.autoplayPronunciationAudio,
            onCheckedChange = actions::onAutoplayPronunciationAudioChange,
            testTag = SettingsScreenTestTags.AUTOPLAY_AUDIO_TOGGLE
        )
        ToggleRow(
            label = "MP3 audio only",
            description = "Only play pronunciation clips available as MP3. Every word has an MP3 version, so nothing is lost — this just guarantees playback on devices that can't play Ogg audio (e.g. e-ink readers).",
            checked = uiState.restrictAudioToMp3,
            onCheckedChange = actions::onRestrictAudioToMp3Change,
            testTag = SettingsScreenTestTags.MP3_ONLY_AUDIO_TOGGLE
        )
        ToggleRow(
            label = "Hide sentence translations",
            description = "Redacts a context sentence's English translation until tapped, so you can try reading the Japanese first.",
            checked = uiState.hideContextSentenceTranslations,
            onCheckedChange = actions::onHideContextSentenceTranslationsChange,
            testTag = SettingsScreenTestTags.HIDE_CONTEXT_SENTENCE_TRANSLATIONS_TOGGLE
        )
    }
}

/** One settings section: the state it renders and the actions it offers. */
@Composable
private fun ReviewsSection(
    uiState: SettingsUiState,
    actions: SettingsActions,
) {
    SectionCard(title = "Reviews", icon = Icons.Default.Quiz) {
        ToggleRow(
            label = "Show item type",
            description = "Displays Radical, Kanji, or Vocabulary below the word during reviews and lesson quizzes — handy on e-ink screens where color alone is hard to read.",
            checked = uiState.showSubjectTypeLabel,
            onCheckedChange = actions::onShowSubjectTypeLabelChange,
            testTag = SettingsScreenTestTags.SHOW_SUBJECT_TYPE_LABEL_TOGGLE
        )
        ToggleRow(
            label = "Total time",
            description = "Shows a running clock above the progress bar for how long the current review/lesson quiz has taken.",
            checked = uiState.showTotalTimer,
            onCheckedChange = actions::onShowTotalTimerChange,
            testTag = SettingsScreenTestTags.SHOW_TOTAL_TIMER_TOGGLE
        )
        ToggleRow(
            label = "Question time",
            description = "Shows a running clock below the progress bar for how long you've spent on the current question.",
            checked = uiState.showQuestionTimer,
            onCheckedChange = actions::onShowQuestionTimerChange,
            testTag = SettingsScreenTestTags.SHOW_QUESTION_TIMER_TOGGLE
        )
        ToggleRow(
            label = "Use system keyboard for reading",
            description = "Disables the built-in romaji converter and sends a Japanese locale hint to your keyboard, so Gboard and similar apps switch language automatically between meaning and reading questions.",
            checked = uiState.useJapaneseKeyboard,
            onCheckedChange = actions::onUseJapaneseKeyboardChange,
            testTag = SettingsScreenTestTags.JAPANESE_KEYBOARD_TOGGLE
        )
        ToggleRow(
            label = "Accept close-enough answers",
            description = "Allows small typos in meaning answers (a couple of letters off from a correct answer still counts). Turn off to require an exact match.",
            checked = uiState.closeEnoughAnswersEnabled,
            onCheckedChange = actions::onCloseEnoughAnswersEnabledChange,
            testTag = SettingsScreenTestTags.CLOSE_ENOUGH_ANSWERS_TOGGLE
        )
        ToggleRow(
            label = "Require a tap to reveal meaning answers",
            description = "On a wrong meaning answer, shows whether you were right or wrong first, then requires a separate tap to reveal the correct meaning — so you can keep thinking about it before you see it.",
            checked = uiState.requireTapToRevealMeaningAnswer,
            onCheckedChange = actions::onRequireTapToRevealMeaningAnswerChange,
            testTag = SettingsScreenTestTags.REQUIRE_TAP_TO_REVEAL_MEANING_ANSWER_TOGGLE
        )
        ToggleRow(
            label = "Require a tap to reveal reading answers",
            description = "On a wrong reading answer, shows whether you were right or wrong first, then requires a separate tap to reveal the correct reading (and its audio/pitch-accent hint, if enabled below) — so you can keep thinking about it before you see it.",
            checked = uiState.requireTapToRevealReadingAnswer,
            onCheckedChange = actions::onRequireTapToRevealReadingAnswerChange,
            testTag = SettingsScreenTestTags.REQUIRE_TAP_TO_REVEAL_READING_ANSWER_TOGGLE
        )
        ToggleRow(
            label = "Show reading & pitch accent on answer",
            description = "After answering a reading question during lessons and reviews, shows the word's reading and its pitch-accent pattern above the character.",
            checked = uiState.showAnswerReadingPitchAccent,
            onCheckedChange = actions::onShowAnswerReadingPitchAccentChange,
            testTag = SettingsScreenTestTags.ANSWER_READING_PITCH_ACCENT_TOGGLE
        )
    }
}

/** One settings section: the state it renders and the actions it offers. */
@Composable
private fun NotificationsSection(
    uiState: SettingsUiState,
    actions: SettingsActions,
    onNotificationsEnabledChange: (Boolean) -> Unit,
) {
    SectionCard(title = "Notifications", icon = Icons.Default.Notifications) {
        ToggleRow(
            label = "Enable notifications",
            description = "Turn on to receive the alerts below.",
            checked = uiState.notificationsEnabled,
            onCheckedChange = onNotificationsEnabledChange,
            testTag = SettingsScreenTestTags.NOTIFICATIONS_MASTER_TOGGLE
        )

        if (uiState.notificationsEnabled) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            NotificationGroupLabel("Reviews")
            ToggleRow(
                label = "Reviews available",
                description = "Notifies you as soon as new reviews are ready.",
                checked = uiState.reviewsAvailableEnabled,
                onCheckedChange = actions::onReviewsAvailableEnabledChange,
                testTag = SettingsScreenTestTags.REVIEWS_AVAILABLE_TOGGLE
            )
            ToggleRow(
                label = "Review backlog warning",
                description = "Warns you when unanswered reviews pile up past the threshold below. Won't repeat more than once every 6 hours.",
                checked = uiState.reviewsBacklogEnabled,
                onCheckedChange = actions::onReviewsBacklogEnabledChange,
                testTag = SettingsScreenTestTags.REVIEWS_BACKLOG_TOGGLE
            )
            if (uiState.reviewsBacklogEnabled) {
                StepperRow(
                    label = "Backlog threshold",
                    value = uiState.backlogThreshold,
                    onValueChange = actions::onBacklogThresholdChange,
                    testTags = SettingsScreenTestTags.BACKLOG_THRESHOLD_TAGS,
                    step = 5,
                    range = BACKLOG_THRESHOLD_RANGE
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            NotificationGroupLabel("Reminders")
            ToggleRow(
                label = "Daily study reminder",
                description = "A one-time nudge at the hour below, sent only if you haven't studied yet that day.",
                checked = uiState.dailyReminderEnabled,
                onCheckedChange = actions::onDailyReminderEnabledChange,
                testTag = SettingsScreenTestTags.DAILY_REMINDER_TOGGLE
            )
            if (uiState.dailyReminderEnabled) {
                StepperRow(
                    label = "Reminder hour",
                    value = uiState.dailyReminderHour,
                    onValueChange = { actions.onDailyReminderHourChange(it.mod(24)) },
                    testTags = SettingsScreenTestTags.DAILY_REMINDER_HOUR_TAGS,
                    valueLabel = { formatHour(it) }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            NotificationGroupLabel("Quiet hours")
            ToggleRow(
                label = "Quiet hours",
                description = "Holds back review/backlog alerts during the window below and delivers them right after it ends. The daily reminder is skipped instead of delayed.",
                checked = uiState.quietHoursEnabled,
                onCheckedChange = actions::onQuietHoursEnabledChange,
                testTag = SettingsScreenTestTags.QUIET_HOURS_TOGGLE
            )
            if (uiState.quietHoursEnabled) {
                StepperRow(
                    label = "Quiet hours start",
                    value = uiState.quietHoursStartHour,
                    onValueChange = { actions.onQuietHoursStartHourChange(it.mod(24)) },
                    testTags = SettingsScreenTestTags.QUIET_HOURS_START_TAGS,
                    valueLabel = { formatHour(it) }
                )
                StepperRow(
                    label = "Quiet hours end",
                    value = uiState.quietHoursEndHour,
                    onValueChange = { actions.onQuietHoursEndHourChange(it.mod(24)) },
                    testTags = SettingsScreenTestTags.QUIET_HOURS_END_TAGS,
                    valueLabel = { formatHour(it) }
                )
            }
        }
    }
}

/** One settings section: the state it renders and the actions it offers. */
@Composable
private fun FriendsSection(
    onOpenLeaderboard: () -> Unit,
) {
    SectionCard(title = "Friends & Competition", icon = Icons.Default.Group) {
        Text(
            text = "Add friends' read-only API tokens to compare progress on the dashboard.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenLeaderboard() }
                .padding(vertical = 8.dp)
        ) {
            Icon(Icons.Default.Group, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Text("Manage friends", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** One settings section: the state it renders and the actions it offers. */
@Composable
private fun DataSection(
    uiState: SettingsUiState,
    actions: SettingsActions,
) {
    var showFullRefreshConfirm by remember { mutableStateOf(false) }
    SectionCard(title = "Data", icon = Icons.Default.Sync) {
        Text(
            text = "Re-downloads your entire WaniKani library from scratch. Useful if some content looks wrong or missing after an app update.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !uiState.isFullRefreshing) { showFullRefreshConfirm = true }
                .testTag(SettingsScreenTestTags.FULL_REFRESH_ROW)
                .padding(vertical = 8.dp)
        ) {
            Text("Full refresh", style = MaterialTheme.typography.bodyLarge)
            if (uiState.isFullRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp).testTag(SettingsScreenTestTags.FULL_REFRESH_PROGRESS),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Sync, contentDescription = null)
            }
        }
        val fullRefreshError = uiState.fullRefreshError
        if (fullRefreshError != null) {
            Text(
                text = fullRefreshError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(SettingsScreenTestTags.FULL_REFRESH_ERROR_TEXT)
            )
        }
    }

    if (showFullRefreshConfirm) {
        ConfirmationDialog(
            title = "Full refresh?",
            text = "This re-downloads your entire WaniKani library from scratch instead of just what's changed. It may take longer than a normal sync and use more data.",
            confirmLabel = "Refresh",
            onConfirm = {
                showFullRefreshConfirm = false
                actions.onFullRefreshRequested()
            },
            onDismiss = { showFullRefreshConfirm = false },
            confirmButtonTestTag = SettingsScreenTestTags.FULL_REFRESH_CONFIRM_BUTTON
        )
    }
}
