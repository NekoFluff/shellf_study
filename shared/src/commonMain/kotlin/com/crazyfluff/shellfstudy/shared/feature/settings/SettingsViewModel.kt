package com.crazyfluff.shellfstudy.shared.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.shared.data.ApiResult
import com.crazyfluff.shellfstudy.shared.data.AppSettings
import com.crazyfluff.shellfstudy.shared.data.DashboardCacheRepository
import com.crazyfluff.shellfstudy.shared.data.LogoutCoordinator
import com.crazyfluff.shellfstudy.shared.data.NotificationSettings
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.ThemeMode
import com.crazyfluff.shellfstudy.shared.data.model.ReviewPriority
import com.crazyfluff.shellfstudy.shared.notifications.NotificationCoordinator
import com.crazyfluff.shellfstudy.shared.notifications.NotificationScheduler
import com.crazyfluff.shellfstudy.shared.sync.SyncOrchestrator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val app: AppSettings = AppSettings(),
    val notifications: NotificationSettings = NotificationSettings(),
    val fullRefresh: FullRefreshStatus = FullRefreshStatus.Idle,
    /** Who's signed in, from the dashboard's last-known summary. Null before the first sync. */
    val account: AccountSummary? = null,
    /** Set once a log out from this screen has finished. The route then leaves for sign-in. */
    val isLoggedOut: Boolean = false
) {
    val isFullRefreshing: Boolean get() = fullRefresh == FullRefreshStatus.InFlight
    val fullRefreshError: String? get() = (fullRefresh as? FullRefreshStatus.Failed)?.message
}

/** The signed-in account, as the Account group shows it. */
data class AccountSummary(val username: String, val level: Int)

/** The settings screen's "re-download everything" action. */
sealed interface FullRefreshStatus {
    data object Idle : FullRefreshStatus
    data object InFlight : FullRefreshStatus
    data class Failed(val message: String) : FullRefreshStatus
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val notificationCoordinator: NotificationCoordinator,
    private val notificationScheduler: NotificationScheduler,
    private val syncOrchestrator: SyncOrchestrator,
    private val logoutCoordinator: LogoutCoordinator,
    dashboardCacheRepository: DashboardCacheRepository
) : ViewModel(), SettingsActions {

    private val fullRefresh = MutableStateFlow<FullRefreshStatus>(FullRefreshStatus.Idle)
    private val isLoggedOut = MutableStateFlow(false)

    private val account = dashboardCacheRepository.cachedSummary.map { summary ->
        summary?.let { AccountSummary(username = it.username, level = it.level) }
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        settingsRepository.notificationSettings,
        fullRefresh,
        account,
        isLoggedOut,
        ::SettingsUiState
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    override fun onDailyLessonGoalChange(goal: Int) {
        viewModelScope.launch { settingsRepository.setDailyLessonGoal(goal) }
    }

    override fun onDailyStudyMinutesGoalChange(minutes: Int) {
        viewModelScope.launch { settingsRepository.setDailyStudyMinutesGoal(minutes) }
    }

    override fun onLessonBatchSizeChange(size: Int) {
        viewModelScope.launch { settingsRepository.setLessonBatchSize(size) }
    }

    override fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    override fun onShowPitchAccentChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShowPitchAccent(enabled) }
    }

    override fun onAutoplayPronunciationAudioChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoplayPronunciationAudio(enabled) }
    }

    override fun onRestrictAudioToMp3Change(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setRestrictAudioToMp3(enabled) }
    }

    override fun onShowSubjectTypeLabelChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShowSubjectTypeLabel(enabled) }
    }

    override fun onShowTotalTimerChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShowTotalTimer(enabled) }
    }

    override fun onShowQuestionTimerChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShowQuestionTimer(enabled) }
    }

    override fun onShowStrokeOrderChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShowStrokeOrder(enabled) }
    }

    override fun onUseJapaneseKeyboardChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setUseJapaneseKeyboard(enabled) }
    }

    override fun onCloseEnoughAnswersEnabledChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setCloseEnoughAnswersEnabled(enabled) }
    }

    override fun onShowAnswerReadingPitchAccentChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShowAnswerReadingPitchAccent(enabled) }
    }

    override fun onHideContextSentenceTranslationsChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHideContextSentenceTranslations(enabled) }
    }

    override fun onRequireTapToRevealMeaningAnswerChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setRequireTapToRevealMeaningAnswer(enabled) }
    }

    override fun onRequireTapToRevealReadingAnswerChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setRequireTapToRevealReadingAnswer(enabled) }
    }

    override fun onReviewPriorityChange(priority: ReviewPriority) {
        viewModelScope.launch { settingsRepository.setReviewPriority(priority) }
    }

    /**
     * Called after the system permission prompt resolves (API 33+) — persists only what was
     * actually granted. Returns the launched [kotlinx.coroutines.Job] (silently discarded by
     * production callers via Kotlin's Unit-conversion) so tests can `join()` it instead of racing
     * a separately-observed state Flow against this coroutine's own completion.
     */
    fun onNotificationsPermissionResult(granted: Boolean) = onNotificationsEnabledChange(granted)

    /** Turning the toggle off, or on below API 33 where no runtime prompt is needed. */
    fun onNotificationsEnabledChange(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setNotificationsEnabled(enabled)
        if (enabled) notificationCoordinator.rescheduleDailyReminder() else notificationScheduler.cancelAll()
    }

    override fun onReviewsAvailableEnabledChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setReviewsAvailableEnabled(enabled) }
    }

    override fun onReviewsBacklogEnabledChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setReviewsBacklogEnabled(enabled) }
    }

    /** Reschedules on enable so the reminder starts now instead of after the next sync; on disable
     *  the pending wakeup stays and simply posts nothing (see `rescheduleLevelUpReminder`). */
    override fun onLevelUpRemindersEnabledChange(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setLevelUpRemindersEnabled(enabled)
        if (enabled) notificationCoordinator.rescheduleLevelUpReminder()
    }

    override fun onBacklogThresholdChange(threshold: Int) {
        viewModelScope.launch { settingsRepository.setBacklogThreshold(threshold) }
    }

    override fun onDailyReminderEnabledChange(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDailyReminderEnabled(enabled)
            notificationCoordinator.rescheduleDailyReminder()
        }
    }

    override fun onDailyReminderHourChange(hour: Int) = viewModelScope.launch {
        settingsRepository.setDailyReminderHour(hour)
        notificationCoordinator.rescheduleDailyReminder()
    }

    override fun onQuietHoursEnabledChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setQuietHoursEnabled(enabled) }
    }

    override fun onQuietHoursStartHourChange(hour: Int) {
        viewModelScope.launch { settingsRepository.setQuietHoursStartHour(hour) }
    }

    override fun onQuietHoursEndHourChange(hour: Int) {
        viewModelScope.launch { settingsRepository.setQuietHoursEndHour(hour) }
    }

    /**
     * Re-downloads every resource from scratch (see [SyncOrchestrator.fullRefresh]) — unlike the
     * dashboard's pull-to-refresh, this bypasses the `updated_after` cursor entirely, so it's the
     * only way to recover on-device data left wrong by a client-side mapping bug that's since been
     * fixed but whose bad output was already persisted.
     */
    override fun onFullRefreshRequested() {
        viewModelScope.launch {
            fullRefresh.value = FullRefreshStatus.InFlight
            fullRefresh.value = when (val result = syncOrchestrator.fullRefresh()) {
                is ApiResult.Success -> FullRefreshStatus.Idle
                is ApiResult.Error -> FullRefreshStatus.Failed(result.message)
            }
        }
    }

    /**
     * Logs out through [LogoutCoordinator], the same sequence the dashboard runs when the token stops
     * working. Returns the launched [kotlinx.coroutines.Job] so tests can `join()` it, like
     * [onNotificationsEnabledChange].
     */
    override fun onLogOutRequested() = viewModelScope.launch {
        logoutCoordinator.logout()
        isLoggedOut.value = true
    }
}
