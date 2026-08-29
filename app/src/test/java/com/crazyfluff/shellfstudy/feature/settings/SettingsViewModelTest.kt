package com.crazyfluff.shellfstudy.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.shared.sync.SyncOrchestrator
import com.crazyfluff.shellfstudy.fakes.FakeNotificationCoordinator
import com.crazyfluff.shellfstudy.fakes.FakeNotificationScheduler
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.crazyfluff.shellfstudy.fakes.emptyResponse
import com.crazyfluff.shellfstudy.fakes.jsonResponse
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.ThemeMode
import com.crazyfluff.shellfstudy.shared.feature.settings.SettingsViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
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

    /**
     * Real [SyncOrchestrator] backed by a local server rather than a fake — [SyncOrchestrator] isn't
     * an interface, and this codebase's convention is real collaborators over mocks. Tests that don't
     * exercise `onFullRefreshRequested` never make it fire a request, so this default (pointed at
     * nothing reachable) is safe for every other test in this file.
     */
    private fun createViewModel(
        syncOrchestrator: SyncOrchestrator = buildTestRepositories("http://localhost/", defaultDispatcher = mainDispatcherRule.dispatcher).syncOrchestrator
    ): SettingsViewModel {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(mainDispatcherRule.dispatcher + SupervisorJob()),
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        settingsRepository = SettingsRepository(dataStore)
        notificationCoordinator = FakeNotificationCoordinator()
        notificationScheduler = FakeNotificationScheduler()
        return SettingsViewModel(settingsRepository, notificationCoordinator, notificationScheduler, syncOrchestrator)
    }

    private fun emptyCollectionResponse(objectType: String): MockResponse =
        jsonResponse("""{"object":"$objectType","url":"https://api.wanikani.com/v2/$objectType","data":[]}""")

    @Test
    fun `onDailyLessonGoalChange updates the state`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().dailyLessonGoal).isEqualTo(15)

            viewModel.onDailyLessonGoalChange(20)
            assertThat(awaitItem().dailyLessonGoal).isEqualTo(20)
        }
    }

    @Test
    fun `onThemeModeChange updates the state`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().themeMode).isEqualTo(ThemeMode.SYSTEM)

            viewModel.onThemeModeChange(ThemeMode.DARK)
            assertThat(awaitItem().themeMode).isEqualTo(ThemeMode.DARK)
        }
    }

    @Test
    fun `onThemeModeChange updates the state to eink`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().themeMode).isEqualTo(ThemeMode.SYSTEM)

            viewModel.onThemeModeChange(ThemeMode.EINK)
            assertThat(awaitItem().themeMode).isEqualTo(ThemeMode.EINK)
        }
    }

    @Test
    fun `onShowPitchAccentChange updates the state`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().showPitchAccent).isTrue()

            viewModel.onShowPitchAccentChange(false)
            assertThat(awaitItem().showPitchAccent).isFalse()
        }
    }

    @Test
    fun `onAutoplayPronunciationAudioChange updates the state`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().autoplayPronunciationAudio).isTrue()

            viewModel.onAutoplayPronunciationAudioChange(false)
            assertThat(awaitItem().autoplayPronunciationAudio).isFalse()
        }
    }

    @Test
    fun `onRestrictAudioToMp3Change updates the state`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().restrictAudioToMp3).isFalse()

            viewModel.onRestrictAudioToMp3Change(true)
            assertThat(awaitItem().restrictAudioToMp3).isTrue()
        }
    }

    @Test
    fun `onShowSubjectTypeLabelChange updates the state`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().showSubjectTypeLabel).isFalse()

            viewModel.onShowSubjectTypeLabelChange(true)
            assertThat(awaitItem().showSubjectTypeLabel).isTrue()
        }
    }

    @Test
    fun `onShowTotalTimerChange updates the state`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().showTotalTimer).isFalse()

            viewModel.onShowTotalTimerChange(true)
            assertThat(awaitItem().showTotalTimer).isTrue()
        }
    }

    @Test
    fun `onShowQuestionTimerChange updates the state`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().showQuestionTimer).isFalse()

            viewModel.onShowQuestionTimerChange(true)
            assertThat(awaitItem().showQuestionTimer).isTrue()
        }
    }

    @Test
    fun `onCloseEnoughAnswersEnabledChange updates the state`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().closeEnoughAnswersEnabled).isTrue()

            viewModel.onCloseEnoughAnswersEnabledChange(false)
            assertThat(awaitItem().closeEnoughAnswersEnabled).isFalse()
        }
    }

    @Test
    fun `uiState reflects opt-in notification defaults`() = runTest(mainDispatcherRule.dispatcher) {
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
    fun `enabling notifications persists and reschedules the daily reminder`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.onNotificationsEnabledChange(true).join()

        assertThat(settingsRepository.notificationSettings.first().notificationsEnabled).isTrue()
        assertThat(notificationCoordinator.rescheduleDailyReminderCallCount).isEqualTo(1)
        assertThat(notificationScheduler.cancelAllCallCount).isEqualTo(0)
    }

    @Test
    fun `disabling notifications persists and cancels all scheduled work`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        viewModel.onNotificationsEnabledChange(true).join()

        viewModel.onNotificationsEnabledChange(false).join()

        assertThat(settingsRepository.notificationSettings.first().notificationsEnabled).isFalse()
        assertThat(notificationScheduler.cancelAllCallCount).isEqualTo(1)
    }

    @Test
    fun `permission result persists only what was actually granted`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.onNotificationsPermissionResult(granted = true).join()
        assertThat(settingsRepository.notificationSettings.first().notificationsEnabled).isTrue()
        assertThat(notificationCoordinator.rescheduleDailyReminderCallCount).isEqualTo(1)

        viewModel.onNotificationsPermissionResult(granted = false).join()
        assertThat(settingsRepository.notificationSettings.first().notificationsEnabled).isFalse()
        assertThat(notificationScheduler.cancelAllCallCount).isEqualTo(1)
    }

    @Test
    fun `changing the daily reminder hour reschedules it`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.onDailyReminderHourChange(9).join()

        assertThat(settingsRepository.notificationSettings.first().dailyReminderHour).isEqualTo(9)
        assertThat(notificationCoordinator.rescheduleDailyReminderCallCount).isEqualTo(1)
    }

    @Test
    fun `category toggles persist without touching scheduling`() = runTest(mainDispatcherRule.dispatcher) {
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
    fun `backlog threshold and quiet hours persist`() = runTest(mainDispatcherRule.dispatcher) {
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

    @Test
    fun `full refresh reports loading then clears on success`() = runTest(mainDispatcherRule.dispatcher) {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore('?')
                return when {
                    path.startsWith("/spaced_repetition_systems") -> emptyCollectionResponse("srs_system")
                    path.startsWith("/subjects") -> emptyCollectionResponse("kanji")
                    path.startsWith("/assignments") -> emptyCollectionResponse("assignment")
                    path.startsWith("/review_statistics") -> emptyCollectionResponse("review_statistic")
                    path.startsWith("/study_materials") -> emptyCollectionResponse("study_material")
                    path.startsWith("/level_progressions") -> emptyCollectionResponse("level_progression")
                    else -> emptyResponse(404)
                }
            }
        }
        server.start()
        val viewModel = createViewModel(buildTestRepositories(server.url("/").toString(), defaultDispatcher = mainDispatcherRule.dispatcher).syncOrchestrator)

        viewModel.uiState.test {
            assertThat(awaitItem().isFullRefreshing).isFalse()

            viewModel.onFullRefreshRequested()
            assertThat(awaitItem().isFullRefreshing).isTrue()

            val finalState = awaitItem()
            assertThat(finalState.isFullRefreshing).isFalse()
            assertThat(finalState.fullRefreshError).isNull()
        }
        server.shutdown()
    }

    @Test
    fun `full refresh surfaces an error message on failure`() = runTest(mainDispatcherRule.dispatcher) {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = emptyResponse(500)
        }
        server.start()
        val viewModel = createViewModel(buildTestRepositories(server.url("/").toString(), defaultDispatcher = mainDispatcherRule.dispatcher).syncOrchestrator)

        viewModel.uiState.test {
            assertThat(awaitItem().isFullRefreshing).isFalse()

            viewModel.onFullRefreshRequested()
            assertThat(awaitItem().isFullRefreshing).isTrue()

            val finalState = awaitItem()
            assertThat(finalState.isFullRefreshing).isFalse()
            assertThat(finalState.fullRefreshError).isNotNull()
        }
        server.shutdown()
    }
}
