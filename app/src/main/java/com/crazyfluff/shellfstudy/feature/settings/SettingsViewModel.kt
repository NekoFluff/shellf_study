package com.crazyfluff.shellfstudy.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.core.data.SettingsRepository
import com.crazyfluff.shellfstudy.core.data.ThemeMode
import com.crazyfluff.shellfstudy.core.notifications.NotificationCoordinator
import com.crazyfluff.shellfstudy.core.notifications.NotificationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val dailyLessonGoal: Int = 15,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showPitchAccent: Boolean = true,
    val autoplayPronunciationAudio: Boolean = true,
    val restrictAudioToMp3: Boolean = false,
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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val notificationCoordinator: NotificationCoordinator,
    private val notificationScheduler: NotificationScheduler
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        settingsRepository.notificationSettings
    ) { app, notif ->
        SettingsUiState(
            dailyLessonGoal = app.dailyLessonGoal,
            themeMode = app.themeMode,
            showPitchAccent = app.showPitchAccent,
            autoplayPronunciationAudio = app.autoplayPronunciationAudio,
            restrictAudioToMp3 = app.restrictAudioToMp3,
            notificationsEnabled = notif.notificationsEnabled,
            reviewsAvailableEnabled = notif.reviewsAvailableEnabled,
            reviewsBacklogEnabled = notif.reviewsBacklogEnabled,
            backlogThreshold = notif.backlogThreshold,
            dailyReminderEnabled = notif.dailyReminderEnabled,
            dailyReminderHour = notif.dailyReminderHour,
            quietHoursEnabled = notif.quietHoursEnabled,
            quietHoursStartHour = notif.quietHoursStartHour,
            quietHoursEndHour = notif.quietHoursEndHour
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onDailyLessonGoalChange(goal: Int) {
        viewModelScope.launch { settingsRepository.setDailyLessonGoal(goal) }
    }

    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun onShowPitchAccentChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShowPitchAccent(enabled) }
    }

    fun onAutoplayPronunciationAudioChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoplayPronunciationAudio(enabled) }
    }

    fun onRestrictAudioToMp3Change(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setRestrictAudioToMp3(enabled) }
    }

    /**
     * Called after the system permission prompt resolves (API 33+) — persists only what was
     * actually granted. Returns the launched [kotlinx.coroutines.Job] (silently discarded by
     * production callers via Kotlin's Unit-conversion) so tests can `join()` it instead of racing
     * a separately-observed state Flow against this coroutine's own completion.
     */
    fun onNotificationsPermissionResult(granted: Boolean) = viewModelScope.launch {
        settingsRepository.setNotificationsEnabled(granted)
        if (granted) notificationCoordinator.rescheduleDailyReminder() else notificationScheduler.cancelAll()
    }

    /** Turning the toggle off, or on below API 33 where no runtime prompt is needed. */
    fun onNotificationsEnabledChange(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setNotificationsEnabled(enabled)
        if (enabled) notificationCoordinator.rescheduleDailyReminder() else notificationScheduler.cancelAll()
    }

    fun onReviewsAvailableEnabledChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setReviewsAvailableEnabled(enabled) }
    }

    fun onReviewsBacklogEnabledChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setReviewsBacklogEnabled(enabled) }
    }

    fun onBacklogThresholdChange(threshold: Int) {
        viewModelScope.launch { settingsRepository.setBacklogThreshold(threshold) }
    }

    fun onDailyReminderEnabledChange(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setDailyReminderEnabled(enabled)
        notificationCoordinator.rescheduleDailyReminder()
    }

    fun onDailyReminderHourChange(hour: Int) = viewModelScope.launch {
        settingsRepository.setDailyReminderHour(hour)
        notificationCoordinator.rescheduleDailyReminder()
    }

    fun onQuietHoursEnabledChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setQuietHoursEnabled(enabled) }
    }

    fun onQuietHoursStartHourChange(hour: Int) {
        viewModelScope.launch { settingsRepository.setQuietHoursStartHour(hour) }
    }

    fun onQuietHoursEndHourChange(hour: Int) {
        viewModelScope.launch { settingsRepository.setQuietHoursEndHour(hour) }
    }
}
