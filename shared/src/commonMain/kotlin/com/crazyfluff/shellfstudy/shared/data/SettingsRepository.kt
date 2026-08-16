package com.crazyfluff.shellfstudy.shared.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

const val DEFAULT_DAILY_LESSON_GOAL = 15

enum class ThemeMode { SYSTEM, LIGHT, DARK, EINK }

data class AppSettings(
    val dailyLessonGoal: Int = DEFAULT_DAILY_LESSON_GOAL,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showPitchAccent: Boolean = true,
    val autoplayPronunciationAudio: Boolean = true,
    val restrictAudioToMp3: Boolean = false,
    val showSubjectTypeLabel: Boolean = false,
    val showTotalTimer: Boolean = false,
    val showQuestionTimer: Boolean = false,
    val showStrokeOrder: Boolean = true,
    val useJapaneseKeyboard: Boolean = false
)

data class NotificationSettings(
    val notificationsEnabled: Boolean = false,
    val reviewsAvailableEnabled: Boolean = true,
    val reviewsBacklogEnabled: Boolean = true,
    val backlogThreshold: Int = 100,
    val dailyReminderEnabled: Boolean = true,
    val dailyReminderHour: Int = 20,
    val quietHoursEnabled: Boolean = true,
    val quietHoursStartHour: Int = 22,
    val quietHoursEndHour: Int = 7
)

class SettingsRepository(
    private val dataStore: DataStore<Preferences>
) {
    private val dailyLessonGoalKey = intPreferencesKey("daily_lesson_goal")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val showPitchAccentKey = booleanPreferencesKey("show_pitch_accent")
    private val autoplayPronunciationAudioKey = booleanPreferencesKey("autoplay_pronunciation_audio")
    private val restrictAudioToMp3Key = booleanPreferencesKey("restrict_audio_to_mp3")
    private val showSubjectTypeLabelKey = booleanPreferencesKey("show_subject_type_label")
    private val showTotalTimerKey = booleanPreferencesKey("show_total_timer")
    private val showQuestionTimerKey = booleanPreferencesKey("show_question_timer")
    private val showStrokeOrderKey = booleanPreferencesKey("show_stroke_order")
    private val useJapaneseKeyboardKey = booleanPreferencesKey("use_japanese_keyboard")

    private val notificationsEnabledKey = booleanPreferencesKey("notifications_enabled")
    private val reviewsAvailableEnabledKey = booleanPreferencesKey("notif_reviews_available_enabled")
    private val reviewsBacklogEnabledKey = booleanPreferencesKey("notif_reviews_backlog_enabled")
    private val backlogThresholdKey = intPreferencesKey("notif_backlog_threshold")
    private val dailyReminderEnabledKey = booleanPreferencesKey("notif_daily_reminder_enabled")
    private val dailyReminderHourKey = intPreferencesKey("notif_daily_reminder_hour")
    private val quietHoursEnabledKey = booleanPreferencesKey("notif_quiet_hours_enabled")
    private val quietHoursStartHourKey = intPreferencesKey("notif_quiet_hours_start_hour")
    private val quietHoursEndHourKey = intPreferencesKey("notif_quiet_hours_end_hour")

    // dataStore is a single app-wide DataStore<Preferences> instance shared by every repository
    // that persists key-value state — ReviewSessionRepository, OutboxRepository, TokenRepository,
    // etc. dataStore.data re-emits on *every* write to that shared file, regardless of which key
    // changed, so without distinctUntilChanged() here, an unrelated write elsewhere (e.g.
    // persisting the review queue mid-session) would re-trigger every collector of these flows —
    // including ReviewViewModel/LessonViewModel's settings collector, which calls
    // _uiState.update() on each emission. That spurious recomposition landing mid-animation (the
    // rank-change badge's entrance, in particular) is what caused visibly dropped frames.
    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            dailyLessonGoal = prefs[dailyLessonGoalKey] ?: DEFAULT_DAILY_LESSON_GOAL,
            themeMode = prefs[themeModeKey]?.let { raw -> runCatching { ThemeMode.valueOf(raw) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            showPitchAccent = prefs[showPitchAccentKey] ?: true,
            autoplayPronunciationAudio = prefs[autoplayPronunciationAudioKey] ?: true,
            restrictAudioToMp3 = prefs[restrictAudioToMp3Key] ?: false,
            showSubjectTypeLabel = prefs[showSubjectTypeLabelKey] ?: false,
            showTotalTimer = prefs[showTotalTimerKey] ?: false,
            showQuestionTimer = prefs[showQuestionTimerKey] ?: false,
            showStrokeOrder = prefs[showStrokeOrderKey] ?: true,
            useJapaneseKeyboard = prefs[useJapaneseKeyboardKey] ?: false
        )
    }.distinctUntilChanged()

    val notificationSettings: Flow<NotificationSettings> = dataStore.data.map { prefs ->
        val defaults = NotificationSettings()
        NotificationSettings(
            notificationsEnabled = prefs[notificationsEnabledKey] ?: defaults.notificationsEnabled,
            reviewsAvailableEnabled = prefs[reviewsAvailableEnabledKey] ?: defaults.reviewsAvailableEnabled,
            reviewsBacklogEnabled = prefs[reviewsBacklogEnabledKey] ?: defaults.reviewsBacklogEnabled,
            backlogThreshold = prefs[backlogThresholdKey] ?: defaults.backlogThreshold,
            dailyReminderEnabled = prefs[dailyReminderEnabledKey] ?: defaults.dailyReminderEnabled,
            dailyReminderHour = prefs[dailyReminderHourKey] ?: defaults.dailyReminderHour,
            quietHoursEnabled = prefs[quietHoursEnabledKey] ?: defaults.quietHoursEnabled,
            quietHoursStartHour = prefs[quietHoursStartHourKey] ?: defaults.quietHoursStartHour,
            quietHoursEndHour = prefs[quietHoursEndHourKey] ?: defaults.quietHoursEndHour
        )
    }.distinctUntilChanged()

    suspend fun setDailyLessonGoal(goal: Int) {
        dataStore.edit { it[dailyLessonGoalKey] = goal.coerceIn(1, 99) }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun setShowPitchAccent(enabled: Boolean) {
        dataStore.edit { it[showPitchAccentKey] = enabled }
    }

    suspend fun setAutoplayPronunciationAudio(enabled: Boolean) {
        dataStore.edit { it[autoplayPronunciationAudioKey] = enabled }
    }

    suspend fun setRestrictAudioToMp3(enabled: Boolean) {
        dataStore.edit { it[restrictAudioToMp3Key] = enabled }
    }

    suspend fun setShowSubjectTypeLabel(enabled: Boolean) {
        dataStore.edit { it[showSubjectTypeLabelKey] = enabled }
    }

    suspend fun setShowTotalTimer(enabled: Boolean) {
        dataStore.edit { it[showTotalTimerKey] = enabled }
    }

    suspend fun setShowQuestionTimer(enabled: Boolean) {
        dataStore.edit { it[showQuestionTimerKey] = enabled }
    }

    suspend fun setShowStrokeOrder(enabled: Boolean) {
        dataStore.edit { it[showStrokeOrderKey] = enabled }
    }

    suspend fun setUseJapaneseKeyboard(enabled: Boolean) {
        dataStore.edit { it[useJapaneseKeyboardKey] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[notificationsEnabledKey] = enabled }
    }

    suspend fun setReviewsAvailableEnabled(enabled: Boolean) {
        dataStore.edit { it[reviewsAvailableEnabledKey] = enabled }
    }

    suspend fun setReviewsBacklogEnabled(enabled: Boolean) {
        dataStore.edit { it[reviewsBacklogEnabledKey] = enabled }
    }

    suspend fun setBacklogThreshold(threshold: Int) {
        dataStore.edit { it[backlogThresholdKey] = threshold.coerceIn(5, 500) }
    }

    suspend fun setDailyReminderEnabled(enabled: Boolean) {
        dataStore.edit { it[dailyReminderEnabledKey] = enabled }
    }

    suspend fun setDailyReminderHour(hour: Int) {
        dataStore.edit { it[dailyReminderHourKey] = hour.coerceIn(0, 23) }
    }

    suspend fun setQuietHoursEnabled(enabled: Boolean) {
        dataStore.edit { it[quietHoursEnabledKey] = enabled }
    }

    suspend fun setQuietHoursStartHour(hour: Int) {
        dataStore.edit { it[quietHoursStartHourKey] = hour.coerceIn(0, 23) }
    }

    suspend fun setQuietHoursEndHour(hour: Int) {
        dataStore.edit { it[quietHoursEndHourKey] = hour.coerceIn(0, 23) }
    }
}
