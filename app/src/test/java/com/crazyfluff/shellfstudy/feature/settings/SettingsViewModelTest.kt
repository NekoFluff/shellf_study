package com.crazyfluff.shellfstudy.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.shared.sync.SyncOrchestrator
import com.crazyfluff.shellfstudy.fakes.waniKaniCollectionDispatcher
import com.crazyfluff.shellfstudy.fakes.FakeNotificationCoordinator
import com.crazyfluff.shellfstudy.fakes.FakeNotificationScheduler
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.crazyfluff.shellfstudy.fakes.emptyResponse
import com.crazyfluff.shellfstudy.fakes.FakeLevelProgressionDao
import com.crazyfluff.shellfstudy.fakes.FakeSessionDao
import com.crazyfluff.shellfstudy.fakes.FakeSyncScheduler
import com.crazyfluff.shellfstudy.fakes.FakeTokenCipher
import com.crazyfluff.shellfstudy.shared.data.AccountDataCleaner
import com.crazyfluff.shellfstudy.shared.data.DashboardCacheRepository
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummaryRepository
import com.crazyfluff.shellfstudy.shared.data.LessonSessionRepository
import com.crazyfluff.shellfstudy.shared.data.LogoutCoordinator
import com.crazyfluff.shellfstudy.shared.data.OutboxRepository
import com.crazyfluff.shellfstudy.shared.data.ReviewSessionRepository
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.TokenRepository
import com.crazyfluff.shellfstudy.shared.feature.settings.AccountSummary
import com.crazyfluff.shellfstudy.shared.session.LessonSessionController
import com.crazyfluff.shellfstudy.shared.session.ReviewSessionController
import com.crazyfluff.shellfstudy.shared.data.ThemeMode
import com.crazyfluff.shellfstudy.shared.data.model.ReviewPriority
import com.crazyfluff.shellfstudy.shared.feature.settings.SettingsViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
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
    private lateinit var tokenRepository: TokenRepository
    private lateinit var dashboardCacheRepository: DashboardCacheRepository
    private lateinit var syncScheduler: FakeSyncScheduler

    /**
     * Real [SyncOrchestrator] backed by a local server rather than a fake — [SyncOrchestrator] isn't
     * an interface, and this codebase's convention is real collaborators over mocks. Tests that don't
     * exercise `onFullRefreshRequested` never make it fire a request, so this default (pointed at
     * nothing reachable) is safe for every other test in this file.
     */
    private fun createViewModel(
        syncOrchestrator: SyncOrchestrator = buildTestRepositories("http://localhost/", defaultDispatcher = mainDispatcherRule.dispatcher).syncOrchestrator
    ): SettingsViewModel {
        val scope = CoroutineScope(mainDispatcherRule.dispatcher + SupervisorJob())
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        settingsRepository = SettingsRepository(dataStore)
        notificationCoordinator = FakeNotificationCoordinator()
        notificationScheduler = FakeNotificationScheduler()
        tokenRepository = TokenRepository(dataStore, FakeTokenCipher())
        dashboardCacheRepository = DashboardCacheRepository(dataStore)
        syncScheduler = FakeSyncScheduler()
        return SettingsViewModel(
            settingsRepository,
            notificationCoordinator,
            notificationScheduler,
            syncOrchestrator,
            buildLogoutCoordinator(dataStore, scope),
            dashboardCacheRepository
        )
    }

    /** Seeds the dashboard's cached summary, which is where Settings reads the account from. */
    private suspend fun saveCachedAccount(username: String, level: Int) {
        dashboardCacheRepository.save(username, level, lessonCount = 0, reviewCount = 0, syncedAtMillis = 1L)
    }

    /** The real logout sequence over in-memory DAOs, wired the same way DashboardViewModelTest does it. */
    private fun buildLogoutCoordinator(dataStore: DataStore<Preferences>, scope: CoroutineScope): LogoutCoordinator {
        val json = Json { ignoreUnknownKeys = true }
        val repositories = buildTestRepositories("http://localhost/", defaultDispatcher = mainDispatcherRule.dispatcher)
        val sessionDao = FakeSessionDao()
        return LogoutCoordinator(
            tokenRepository = tokenRepository,
            syncScheduler = syncScheduler,
            notificationCoordinator = notificationCoordinator,
            accountDataCleaner = AccountDataCleaner(
                assignmentDao = repositories.assignmentDao,
                reviewStatisticDao = repositories.reviewStatisticDao,
                levelProgressionDao = FakeLevelProgressionDao(),
                syncStateDao = repositories.syncStateDao,
                outboxDao = repositories.outboxDao,
                outboxRepository = OutboxRepository(
                    repositories.outboxDao,
                    repositories.outboxSyncScheduler,
                    dataStore
                ),
                dashboardCacheRepository = dashboardCacheRepository,
                lastSessionSummaryRepository = LastSessionSummaryRepository(dataStore, json),
                reviewSessionController = ReviewSessionController(
                    scope,
                    ReviewSessionRepository(sessionDao, dataStore, json)
                ),
                lessonSessionController = LessonSessionController(
                    scope,
                    LessonSessionRepository(sessionDao, dataStore, json)
                )
            )
        )
    }


    @Test
    fun `onDailyLessonGoalChange updates the state`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().app.dailyLessonGoal).isEqualTo(15)

            viewModel.onDailyLessonGoalChange(20)
            assertThat(awaitItem().app.dailyLessonGoal).isEqualTo(20)
        }
    }

    @Test
    fun `onReviewPriorityChange updates the state and persists`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            // Defaults to the pre-setting behavior, so an existing install sees no change on upgrade.
            assertThat(awaitItem().app.reviewPriority).isEqualTo(ReviewPriority.DEFAULT)

            viewModel.onReviewPriorityChange(ReviewPriority.RANK_UP)
            assertThat(awaitItem().app.reviewPriority).isEqualTo(ReviewPriority.RANK_UP)
        }

        assertThat(settingsRepository.settings.first().reviewPriority).isEqualTo(ReviewPriority.RANK_UP)
    }

    @Test
    fun `onThemeModeChange updates the state`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().app.themeMode).isEqualTo(ThemeMode.SYSTEM)

            viewModel.onThemeModeChange(ThemeMode.DARK)
            assertThat(awaitItem().app.themeMode).isEqualTo(ThemeMode.DARK)
        }
    }

    @Test
    fun `onThemeModeChange updates the state to eink`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().app.themeMode).isEqualTo(ThemeMode.SYSTEM)

            viewModel.onThemeModeChange(ThemeMode.EINK)
            assertThat(awaitItem().app.themeMode).isEqualTo(ThemeMode.EINK)
        }
    }

    @Test
    fun `onShowPitchAccentChange updates the state`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().app.showPitchAccent).isTrue()

            viewModel.onShowPitchAccentChange(false)
            assertThat(awaitItem().app.showPitchAccent).isFalse()
        }
    }

    @Test
    fun `onAutoplayPronunciationAudioChange updates the state`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().app.autoplayPronunciationAudio).isTrue()

            viewModel.onAutoplayPronunciationAudioChange(false)
            assertThat(awaitItem().app.autoplayPronunciationAudio).isFalse()
        }
    }

    @Test
    fun `onRestrictAudioToMp3Change updates the state`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().app.restrictAudioToMp3).isFalse()

            viewModel.onRestrictAudioToMp3Change(true)
            assertThat(awaitItem().app.restrictAudioToMp3).isTrue()
        }
    }

    @Test
    fun `onShowSubjectTypeLabelChange updates the state`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().app.showSubjectTypeLabel).isFalse()

            viewModel.onShowSubjectTypeLabelChange(true)
            assertThat(awaitItem().app.showSubjectTypeLabel).isTrue()
        }
    }

    @Test
    fun `onShowAnswerReadingPitchAccentChange updates the state`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().app.showAnswerReadingPitchAccent).isFalse()

            viewModel.onShowAnswerReadingPitchAccentChange(true)
            assertThat(awaitItem().app.showAnswerReadingPitchAccent).isTrue()
        }
    }

    @Test
    fun `onHideContextSentenceTranslationsChange updates the state`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().app.hideContextSentenceTranslations).isTrue()

            viewModel.onHideContextSentenceTranslationsChange(false)
            assertThat(awaitItem().app.hideContextSentenceTranslations).isFalse()
        }
    }

    @Test
    fun `onShowTotalTimerChange updates the state`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().app.showTotalTimer).isFalse()

            viewModel.onShowTotalTimerChange(true)
            assertThat(awaitItem().app.showTotalTimer).isTrue()
        }
    }

    @Test
    fun `onShowQuestionTimerChange updates the state`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().app.showQuestionTimer).isFalse()

            viewModel.onShowQuestionTimerChange(true)
            assertThat(awaitItem().app.showQuestionTimer).isTrue()
        }
    }

    @Test
    fun `onCloseEnoughAnswersEnabledChange updates the state`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().app.closeEnoughAnswersEnabled).isTrue()

            viewModel.onCloseEnoughAnswersEnabledChange(false)
            assertThat(awaitItem().app.closeEnoughAnswersEnabled).isFalse()
        }
    }

    @Test
    fun `onRequireTapToRevealMeaningAnswerChange updates the state`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().app.requireTapToRevealMeaningAnswer).isFalse()

            viewModel.onRequireTapToRevealMeaningAnswerChange(true)
            assertThat(awaitItem().app.requireTapToRevealMeaningAnswer).isTrue()
        }
    }

    @Test
    fun `onRequireTapToRevealReadingAnswerChange updates the state`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().app.requireTapToRevealReadingAnswer).isFalse()

            viewModel.onRequireTapToRevealReadingAnswerChange(true)
            assertThat(awaitItem().app.requireTapToRevealReadingAnswer).isTrue()
        }
    }

    @Test
    fun `uiState reflects opt-in notification defaults`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.notifications.notificationsEnabled).isFalse()
            assertThat(state.notifications.reviewsAvailableEnabled).isTrue()
            assertThat(state.notifications.backlogThreshold).isEqualTo(100)
            assertThat(state.notifications.dailyReminderHour).isEqualTo(20)
            assertThat(state.notifications.quietHoursStartHour).isEqualTo(22)
            assertThat(state.notifications.quietHoursEndHour).isEqualTo(7)
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
    fun `level-up reminder toggle persists and reschedules only when turned on`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.onLevelUpRemindersEnabledChange(false).join()
        assertThat(settingsRepository.notificationSettings.first().levelUpRemindersEnabled).isFalse()
        assertThat(notificationCoordinator.rescheduleLevelUpReminderCallCount).isEqualTo(0)

        viewModel.onLevelUpRemindersEnabledChange(true).join()
        assertThat(settingsRepository.notificationSettings.first().levelUpRemindersEnabled).isTrue()
        assertThat(notificationCoordinator.rescheduleLevelUpReminderCallCount).isEqualTo(1)
    }

    @Test
    fun `category toggles persist without touching scheduling`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onReviewsAvailableEnabledChange(false)
            assertThat(awaitItem().notifications.reviewsAvailableEnabled).isFalse()
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
            assertThat(awaitItem().notifications.backlogThreshold).isEqualTo(80)
            viewModel.onQuietHoursStartHourChange(23)
            assertThat(awaitItem().notifications.quietHoursStartHour).isEqualTo(23)
            viewModel.onQuietHoursEndHourChange(6)
            assertThat(awaitItem().notifications.quietHoursEndHour).isEqualTo(6)
        }
    }

    @Test
    fun `full refresh reports loading then clears on success`() = runTest(mainDispatcherRule.dispatcher) {
        val server = MockWebServer()
        server.dispatcher = waniKaniCollectionDispatcher()
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
        server.close()
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
        server.close()
    }

    @Test
    fun `account comes from the cached dashboard summary`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().account).isNull()

            saveCachedAccount(username = "koichi", level = 12)

            var state = awaitItem()
            while (state.account == null) state = awaitItem()
            assertThat(state.account).isEqualTo(AccountSummary(username = "koichi", level = 12))
        }
    }

    @Test
    fun `onLogOutRequested clears the token, stops background work, and marks state logged out`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        tokenRepository.saveToken("some-token")
        saveCachedAccount(username = "koichi", level = 12)

        viewModel.uiState.test {
            assertThat(awaitItem().isLoggedOut).isFalse()

            viewModel.onLogOutRequested().join()

            var state = awaitItem()
            while (!state.isLoggedOut) state = awaitItem()
            // The account data cleaner wipes the cached summary as part of the same logout.
            assertThat(state.account).isNull()
            cancelAndIgnoreRemainingEvents()
        }

        tokenRepository.tokenFlow.test { assertThat(awaitItem()).isNull() }
        assertThat(syncScheduler.cancelCallCount).isEqualTo(1)
        assertThat(notificationCoordinator.onLogoutCallCount).isEqualTo(1)
    }
}
