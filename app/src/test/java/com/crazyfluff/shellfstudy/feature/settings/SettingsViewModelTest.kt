package com.crazyfluff.shellfstudy.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.core.data.SettingsRepository
import com.crazyfluff.shellfstudy.core.data.ThemeMode
import com.crazyfluff.shellfstudy.fakes.FakeNotificationCoordinator
import com.crazyfluff.shellfstudy.fakes.FakeNotificationScheduler
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var notificationCoordinator: FakeNotificationCoordinator
    private lateinit var notificationScheduler: FakeNotificationScheduler

    private fun createViewModel(): SettingsViewModel {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        settingsRepository = SettingsRepository(dataStore)
        notificationCoordinator = FakeNotificationCoordinator()
        notificationScheduler = FakeNotificationScheduler()
        return SettingsViewModel(settingsRepository, notificationCoordinator, notificationScheduler)
    }

    @Test
    fun `onDailyLessonGoalChange updates the state`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().dailyLessonGoal).isEqualTo(15)

            viewModel.onDailyLessonGoalChange(20)
            assertThat(awaitItem().dailyLessonGoal).isEqualTo(20)
        }
    }

    @Test
    fun `onThemeModeChange updates the state`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().themeMode).isEqualTo(ThemeMode.SYSTEM)

            viewModel.onThemeModeChange(ThemeMode.DARK)
            assertThat(awaitItem().themeMode).isEqualTo(ThemeMode.DARK)
        }
    }

    @Test
    fun `onThemeModeChange updates the state to eink`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().themeMode).isEqualTo(ThemeMode.SYSTEM)

            viewModel.onThemeModeChange(ThemeMode.EINK)
            assertThat(awaitItem().themeMode).isEqualTo(ThemeMode.EINK)
        }
    }

    @Test
    fun `onShowPitchAccentChange updates the state`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().showPitchAccent).isTrue()

            viewModel.onShowPitchAccentChange(false)
            assertThat(awaitItem().showPitchAccent).isFalse()
        }
    }

    @Test
    fun `onAutoplayPronunciationAudioChange updates the state`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().autoplayPronunciationAudio).isTrue()

            viewModel.onAutoplayPronunciationAudioChange(false)
            assertThat(awaitItem().autoplayPronunciationAudio).isFalse()
        }
    }

    @Test
    fun `uiState reflects opt-in notification defaults`() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.notificationsEnabled).isFalse()
            assertThat(state.reviewsAvailableEnabled).isTrue()
            assertThat(state.backlogThreshold).isEqualTo(100)
            assertThat(state.dailyReminderHour).isEqualTo(20)
            assertThat(state.quietHoursStartHour).isEqualTo(22)
            assertThat(state.quietHoursEndHour).isEqualTo(7)
        }
    }

    // These four tests `.join()` the Job these setters return instead of racing Turbine's
    // observation of the resulting uiState change against this same coroutine's own completion:
    // both are triggered by the same underlying (real-IO-backed) DataStore write settling, so
    // which one a test observes first is nondeterministic. Joining the actual Job sidesteps that
    // race entirely — by the time join() returns, both the persist and the coordinator/scheduler
    // call inside it have definitely happened, in that order.

    @Test
    fun `enabling notifications persists and reschedules the daily reminder`() = runTest {
        val viewModel = createViewModel()

        viewModel.onNotificationsEnabledChange(true).join()

        assertThat(settingsRepository.notificationSettings.first().notificationsEnabled).isTrue()
        assertThat(notificationCoordinator.rescheduleDailyReminderCallCount).isEqualTo(1)
        assertThat(notificationScheduler.cancelAllCallCount).isEqualTo(0)
    }

    @Test
    fun `disabling notifications persists and cancels all scheduled work`() = runTest {
        val viewModel = createViewModel()
        viewModel.onNotificationsEnabledChange(true).join()

        viewModel.onNotificationsEnabledChange(false).join()

        assertThat(settingsRepository.notificationSettings.first().notificationsEnabled).isFalse()
        assertThat(notificationScheduler.cancelAllCallCount).isEqualTo(1)
    }

    @Test
    fun `permission result persists only what was actually granted`() = runTest {
        val viewModel = createViewModel()

        viewModel.onNotificationsPermissionResult(granted = true).join()
        assertThat(settingsRepository.notificationSettings.first().notificationsEnabled).isTrue()
        assertThat(notificationCoordinator.rescheduleDailyReminderCallCount).isEqualTo(1)

        viewModel.onNotificationsPermissionResult(granted = false).join()
        assertThat(settingsRepository.notificationSettings.first().notificationsEnabled).isFalse()
        assertThat(notificationScheduler.cancelAllCallCount).isEqualTo(1)
    }

    @Test
    fun `changing the daily reminder hour reschedules it`() = runTest {
        val viewModel = createViewModel()

        viewModel.onDailyReminderHourChange(9).join()

        assertThat(settingsRepository.notificationSettings.first().dailyReminderHour).isEqualTo(9)
        assertThat(notificationCoordinator.rescheduleDailyReminderCallCount).isEqualTo(1)
    }

    @Test
    fun `category toggles persist without touching scheduling`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onReviewsAvailableEnabledChange(false)
            assertThat(awaitItem().reviewsAvailableEnabled).isFalse()
        }
        assertThat(notificationCoordinator.rescheduleDailyReminderCallCount).isEqualTo(0)
        assertThat(notificationScheduler.cancelAllCallCount).isEqualTo(0)
    }

    @Test
    fun `backlog threshold and quiet hours persist`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onBacklogThresholdChange(80)
            assertThat(awaitItem().backlogThreshold).isEqualTo(80)
            viewModel.onQuietHoursStartHourChange(23)
            assertThat(awaitItem().quietHoursStartHour).isEqualTo(23)
            viewModel.onQuietHoursEndHourChange(6)
            assertThat(awaitItem().quietHoursEndHour).isEqualTo(6)
        }
    }
}
