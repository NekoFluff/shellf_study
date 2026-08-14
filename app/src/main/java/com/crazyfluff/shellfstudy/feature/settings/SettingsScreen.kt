package com.crazyfluff.shellfstudy.feature.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MenuBook
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
import org.koin.androidx.compose.koinViewModel
import com.crazyfluff.shellfstudy.core.designsystem.dialog.ConfirmationDialog
import com.crazyfluff.shellfstudy.shared.designsystem.theme.ShellfStudyTheme
import com.crazyfluff.shellfstudy.shared.data.ThemeMode

object SettingsScreenTestTags {
    const val BACK_BUTTON = "settings_back_button"
    const val LESSON_GOAL_DECREASE = "settings_lesson_goal_decrease"
    const val LESSON_GOAL_INCREASE = "settings_lesson_goal_increase"
    const val LESSON_GOAL_VALUE = "settings_lesson_goal_value"
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
}

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // POST_NOTIFICATIONS is only a runtime permission from API 33 onward — below that, and when
    // turning the toggle off, there's nothing to request, so onNotificationsEnabledChange is
    // called directly instead of going through the launcher.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onNotificationsPermissionResult(granted) }

    SettingsScreen(
        uiState = uiState,
        onDailyLessonGoalChange = viewModel::onDailyLessonGoalChange,
        onThemeModeChange = viewModel::onThemeModeChange,
        onShowPitchAccentChange = viewModel::onShowPitchAccentChange,
        onAutoplayPronunciationAudioChange = viewModel::onAutoplayPronunciationAudioChange,
        onRestrictAudioToMp3Change = viewModel::onRestrictAudioToMp3Change,
        onShowSubjectTypeLabelChange = viewModel::onShowSubjectTypeLabelChange,
        onShowTotalTimerChange = viewModel::onShowTotalTimerChange,
        onShowQuestionTimerChange = viewModel::onShowQuestionTimerChange,
        onNotificationsEnabledChange = { enabled ->
            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.onNotificationsEnabledChange(enabled)
            }
        },
        onReviewsAvailableEnabledChange = viewModel::onReviewsAvailableEnabledChange,
        onReviewsBacklogEnabledChange = viewModel::onReviewsBacklogEnabledChange,
        onBacklogThresholdChange = viewModel::onBacklogThresholdChange,
        onDailyReminderEnabledChange = viewModel::onDailyReminderEnabledChange,
        onDailyReminderHourChange = viewModel::onDailyReminderHourChange,
        onQuietHoursEnabledChange = viewModel::onQuietHoursEnabledChange,
        onQuietHoursStartHourChange = viewModel::onQuietHoursStartHourChange,
        onQuietHoursEndHourChange = viewModel::onQuietHoursEndHourChange,
        onFullRefreshRequested = viewModel::onFullRefreshRequested,
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onDailyLessonGoalChange: (Int) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onShowPitchAccentChange: (Boolean) -> Unit,
    onAutoplayPronunciationAudioChange: (Boolean) -> Unit,
    onRestrictAudioToMp3Change: (Boolean) -> Unit,
    onShowSubjectTypeLabelChange: (Boolean) -> Unit,
    onShowTotalTimerChange: (Boolean) -> Unit,
    onShowQuestionTimerChange: (Boolean) -> Unit,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    onReviewsAvailableEnabledChange: (Boolean) -> Unit,
    onReviewsBacklogEnabledChange: (Boolean) -> Unit,
    onBacklogThresholdChange: (Int) -> Unit,
    onDailyReminderEnabledChange: (Boolean) -> Unit,
    onDailyReminderHourChange: (Int) -> Unit,
    onQuietHoursEnabledChange: (Boolean) -> Unit,
    onQuietHoursStartHourChange: (Int) -> Unit,
    onQuietHoursEndHourChange: (Int) -> Unit,
    onFullRefreshRequested: () -> Unit,
    onBack: () -> Unit
) {
    var showFullRefreshConfirm by remember { mutableStateOf(false) }

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
            SectionCard(title = "Daily lesson goal", icon = Icons.Default.MenuBook) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IconButton(
                        onClick = { onDailyLessonGoalChange(uiState.dailyLessonGoal - 1) },
                        enabled = uiState.dailyLessonGoal > 1,
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
                        onClick = { onDailyLessonGoalChange(uiState.dailyLessonGoal + 1) },
                        enabled = uiState.dailyLessonGoal < 99,
                        modifier = Modifier.testTag(SettingsScreenTestTags.LESSON_GOAL_INCREASE)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase daily lesson goal")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard(title = "Appearance", icon = Icons.Default.Palette) {
                ThemeOptionRow(
                    label = "System default",
                    mode = ThemeMode.SYSTEM,
                    selected = uiState.themeMode,
                    onSelect = onThemeModeChange,
                    testTag = SettingsScreenTestTags.THEME_SYSTEM_OPTION
                )
                ThemeOptionRow(
                    label = "Light",
                    mode = ThemeMode.LIGHT,
                    selected = uiState.themeMode,
                    onSelect = onThemeModeChange,
                    testTag = SettingsScreenTestTags.THEME_LIGHT_OPTION
                )
                ThemeOptionRow(
                    label = "Dark",
                    mode = ThemeMode.DARK,
                    selected = uiState.themeMode,
                    onSelect = onThemeModeChange,
                    testTag = SettingsScreenTestTags.THEME_DARK_OPTION
                )
                ThemeOptionRow(
                    label = "E-Ink (grayscale)",
                    mode = ThemeMode.EINK,
                    selected = uiState.themeMode,
                    onSelect = onThemeModeChange,
                    testTag = SettingsScreenTestTags.THEME_EINK_OPTION
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard(title = "Vocabulary", icon = Icons.Default.Translate) {
                ToggleRow(
                    label = "Show pitch accent",
                    description = "Overlays WaniKani's pitch-accent pattern markers on vocabulary readings.",
                    checked = uiState.showPitchAccent,
                    onCheckedChange = onShowPitchAccentChange,
                    testTag = SettingsScreenTestTags.PITCH_ACCENT_TOGGLE
                )
                ToggleRow(
                    label = "Auto-play pronunciation audio",
                    description = "Plays a word's audio automatically when a reading question is revealed during reviews.",
                    checked = uiState.autoplayPronunciationAudio,
                    onCheckedChange = onAutoplayPronunciationAudioChange,
                    testTag = SettingsScreenTestTags.AUTOPLAY_AUDIO_TOGGLE
                )
                ToggleRow(
                    label = "MP3 audio only",
                    description = "Only play pronunciation clips available as MP3. Every word has an MP3 version, so nothing is lost — this just guarantees playback on devices that can't play Ogg audio (e.g. e-ink readers).",
                    checked = uiState.restrictAudioToMp3,
                    onCheckedChange = onRestrictAudioToMp3Change,
                    testTag = SettingsScreenTestTags.MP3_ONLY_AUDIO_TOGGLE
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard(title = "Reviews", icon = Icons.Default.Quiz) {
                ToggleRow(
                    label = "Show item type",
                    description = "Displays Radical, Kanji, or Vocabulary below the word during reviews and lesson quizzes — handy on e-ink screens where color alone is hard to read.",
                    checked = uiState.showSubjectTypeLabel,
                    onCheckedChange = onShowSubjectTypeLabelChange,
                    testTag = SettingsScreenTestTags.SHOW_SUBJECT_TYPE_LABEL_TOGGLE
                )
                ToggleRow(
                    label = "Total time",
                    description = "Shows a running clock above the progress bar for how long the current review/lesson quiz has taken.",
                    checked = uiState.showTotalTimer,
                    onCheckedChange = onShowTotalTimerChange,
                    testTag = SettingsScreenTestTags.SHOW_TOTAL_TIMER_TOGGLE
                )
                ToggleRow(
                    label = "Question time",
                    description = "Shows a running clock below the progress bar for how long you've spent on the current question.",
                    checked = uiState.showQuestionTimer,
                    onCheckedChange = onShowQuestionTimerChange,
                    testTag = SettingsScreenTestTags.SHOW_QUESTION_TIMER_TOGGLE
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                        onCheckedChange = onReviewsAvailableEnabledChange,
                        testTag = SettingsScreenTestTags.REVIEWS_AVAILABLE_TOGGLE
                    )
                    ToggleRow(
                        label = "Review backlog warning",
                        description = "Warns you when unanswered reviews pile up past the threshold below. Won't repeat more than once every 6 hours.",
                        checked = uiState.reviewsBacklogEnabled,
                        onCheckedChange = onReviewsBacklogEnabledChange,
                        testTag = SettingsScreenTestTags.REVIEWS_BACKLOG_TOGGLE
                    )
                    if (uiState.reviewsBacklogEnabled) {
                        StepperRow(
                            label = "Backlog threshold",
                            value = uiState.backlogThreshold,
                            onValueChange = onBacklogThresholdChange,
                            decreaseTestTag = SettingsScreenTestTags.BACKLOG_THRESHOLD_DECREASE,
                            increaseTestTag = SettingsScreenTestTags.BACKLOG_THRESHOLD_INCREASE,
                            valueTestTag = SettingsScreenTestTags.BACKLOG_THRESHOLD_VALUE,
                            step = 5
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    NotificationGroupLabel("Reminders")
                    ToggleRow(
                        label = "Daily study reminder",
                        description = "A one-time nudge at the hour below, sent only if you haven't studied yet that day.",
                        checked = uiState.dailyReminderEnabled,
                        onCheckedChange = onDailyReminderEnabledChange,
                        testTag = SettingsScreenTestTags.DAILY_REMINDER_TOGGLE
                    )
                    if (uiState.dailyReminderEnabled) {
                        StepperRow(
                            label = "Reminder hour",
                            value = uiState.dailyReminderHour,
                            onValueChange = { onDailyReminderHourChange(it.mod(24)) },
                            decreaseTestTag = SettingsScreenTestTags.DAILY_REMINDER_HOUR_DECREASE,
                            increaseTestTag = SettingsScreenTestTags.DAILY_REMINDER_HOUR_INCREASE,
                            valueTestTag = SettingsScreenTestTags.DAILY_REMINDER_HOUR_VALUE,
                            valueLabel = { formatHour(it) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    NotificationGroupLabel("Quiet hours")
                    ToggleRow(
                        label = "Quiet hours",
                        description = "Holds back review/backlog alerts during the window below and delivers them right after it ends. The daily reminder is skipped instead of delayed.",
                        checked = uiState.quietHoursEnabled,
                        onCheckedChange = onQuietHoursEnabledChange,
                        testTag = SettingsScreenTestTags.QUIET_HOURS_TOGGLE
                    )
                    if (uiState.quietHoursEnabled) {
                        StepperRow(
                            label = "Quiet hours start",
                            value = uiState.quietHoursStartHour,
                            onValueChange = { onQuietHoursStartHourChange(it.mod(24)) },
                            decreaseTestTag = SettingsScreenTestTags.QUIET_HOURS_START_DECREASE,
                            increaseTestTag = SettingsScreenTestTags.QUIET_HOURS_START_INCREASE,
                            valueTestTag = SettingsScreenTestTags.QUIET_HOURS_START_VALUE,
                            valueLabel = { formatHour(it) }
                        )
                        StepperRow(
                            label = "Quiet hours end",
                            value = uiState.quietHoursEndHour,
                            onValueChange = { onQuietHoursEndHourChange(it.mod(24)) },
                            decreaseTestTag = SettingsScreenTestTags.QUIET_HOURS_END_DECREASE,
                            increaseTestTag = SettingsScreenTestTags.QUIET_HOURS_END_INCREASE,
                            valueTestTag = SettingsScreenTestTags.QUIET_HOURS_END_VALUE,
                            valueLabel = { formatHour(it) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                if (uiState.fullRefreshError != null) {
                    Text(
                        text = uiState.fullRefreshError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(SettingsScreenTestTags.FULL_REFRESH_ERROR_TEXT)
                    )
                }
            }
        }
    }

    if (showFullRefreshConfirm) {
        ConfirmationDialog(
            title = "Full refresh?",
            text = "This re-downloads your entire WaniKani library from scratch instead of just what's changed. It may take longer than a normal sync and use more data.",
            confirmLabel = "Refresh",
            onConfirm = {
                showFullRefreshConfirm = false
                onFullRefreshRequested()
            },
            onDismiss = { showFullRefreshConfirm = false },
            confirmButtonTestTag = SettingsScreenTestTags.FULL_REFRESH_CONFIRM_BUTTON
        )
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

/** A +/- stepper, same shape as the daily-lesson-goal control above, wrapping hour values 0-23. */
@Composable
private fun StepperRow(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    decreaseTestTag: String,
    increaseTestTag: String,
    valueTestTag: String,
    step: Int = 1,
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
                modifier = Modifier.testTag(decreaseTestTag)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease $label")
            }
            Text(
                text = valueLabel(value),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag(valueTestTag)
            )
            IconButton(
                onClick = { onValueChange(value + step) },
                modifier = Modifier.testTag(increaseTestTag)
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

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    ShellfStudyTheme {
        SettingsScreen(
            uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM, notificationsEnabled = true),
            onDailyLessonGoalChange = {},
            onThemeModeChange = {},
            onShowPitchAccentChange = {},
            onAutoplayPronunciationAudioChange = {},
            onRestrictAudioToMp3Change = {},
            onShowSubjectTypeLabelChange = {},
            onShowTotalTimerChange = {},
            onShowQuestionTimerChange = {},
            onNotificationsEnabledChange = {},
            onReviewsAvailableEnabledChange = {},
            onReviewsBacklogEnabledChange = {},
            onBacklogThresholdChange = {},
            onDailyReminderEnabledChange = {},
            onDailyReminderHourChange = {},
            onQuietHoursEnabledChange = {},
            onQuietHoursStartHourChange = {},
            onQuietHoursEndHourChange = {},
            onFullRefreshRequested = {},
            onBack = {}
        )
    }
}
