package com.crazyfluff.shellfstudy.feature.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.TokenRepository
import com.crazyfluff.shellfstudy.shared.data.WaniKaniRepository
import com.crazyfluff.shellfstudy.fakes.FakeNotificationCoordinator
import com.crazyfluff.shellfstudy.fakes.FakePitchAccentScrapeScheduler
import com.crazyfluff.shellfstudy.fakes.FakeSyncScheduler
import com.crazyfluff.shellfstudy.fakes.FakeTokenCipher
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.crazyfluff.shellfstudy.shared.feature.auth.AuthViewModel
import com.crazyfluff.shellfstudy.fakes.emptyResponse
import com.crazyfluff.shellfstudy.fakes.jsonResponse
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var tokenRepository: TokenRepository
    private lateinit var waniKaniRepository: WaniKaniRepository
    private lateinit var syncScheduler: FakeSyncScheduler
    private lateinit var pitchAccentScrapeScheduler: FakePitchAccentScrapeScheduler
    private lateinit var notificationCoordinator: FakeNotificationCoordinator
    private lateinit var settingsRepository: SettingsRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val scope = CoroutineScope(mainDispatcherRule.dispatcher + SupervisorJob())
        val tokenDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        val settingsDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tempFolder.newFile("settings.preferences_pb") }
        )
        tokenRepository = TokenRepository(tokenDataStore, FakeTokenCipher())
        settingsRepository = SettingsRepository(settingsDataStore)
        waniKaniRepository = buildTestRepositories(server.url("/").toString()).waniKaniRepository
        syncScheduler = FakeSyncScheduler()
        pitchAccentScrapeScheduler = FakePitchAccentScrapeScheduler()
        notificationCoordinator = FakeNotificationCoordinator()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun createViewModel() =
        AuthViewModel(tokenRepository, waniKaniRepository, syncScheduler, pitchAccentScrapeScheduler, notificationCoordinator, settingsRepository)

    @Test
    fun `submitting a blank token shows a validation error and makes no request`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            awaitItem()

            viewModel.submitToken()

            val afterSubmit = awaitItem()
            assertThat(afterSubmit.errorMessage).isNotNull()
            assertThat(afterSubmit.isAuthenticated).isFalse()
        }
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `submitting a valid token shows notification prompt before authenticating`() = runTest(mainDispatcherRule.dispatcher) {
        server.enqueue(jsonResponse(userJson()))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            viewModel.onTokenInputChange("new-token")
            awaitItem() // input change emission
            viewModel.submitToken()

            var afterSubmit = awaitItem()
            while (afterSubmit.isSubmitting) afterSubmit = awaitItem()
            assertThat(afterSubmit.pendingNotificationRequest).isTrue()
            assertThat(afterSubmit.isAuthenticated).isFalse()
        }
        assertThat(syncScheduler.scheduleCallCount).isEqualTo(1)
        assertThat(notificationCoordinator.onLoginCallCount).isEqualTo(1)
    }

    @Test
    fun `granting notification permission enables notifications and navigates`() = runTest(mainDispatcherRule.dispatcher) {
        server.enqueue(jsonResponse(userJson()))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onTokenInputChange("new-token")
            awaitItem()
            viewModel.submitToken()
            var state = awaitItem()
            while (state.isSubmitting) state = awaitItem()

            viewModel.onNotificationPermissionResult(true)
            state = awaitItem()
            assertThat(state.isAuthenticated).isTrue()
            assertThat(state.pendingNotificationRequest).isFalse()
        }
        assertThat(settingsRepository.notificationSettings.first().notificationsEnabled).isTrue()
    }

    @Test
    fun `denying notification permission still navigates but leaves notifications disabled`() = runTest(mainDispatcherRule.dispatcher) {
        server.enqueue(jsonResponse(userJson()))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onTokenInputChange("new-token")
            awaitItem()
            viewModel.submitToken()
            var state = awaitItem()
            while (state.isSubmitting) state = awaitItem()

            viewModel.onNotificationPermissionResult(false)
            state = awaitItem()
            assertThat(state.isAuthenticated).isTrue()
        }
        assertThat(settingsRepository.notificationSettings.first().notificationsEnabled).isFalse()
    }

    @Test
    fun `submitting an invalid token shows an error and does not authenticate`() = runTest(mainDispatcherRule.dispatcher) {
        server.enqueue(emptyResponse(401))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            viewModel.onTokenInputChange("bad-token")
            awaitItem()
            viewModel.submitToken()

            var afterSubmit = awaitItem()
            while (afterSubmit.isSubmitting) afterSubmit = awaitItem()
            assertThat(afterSubmit.isAuthenticated).isFalse()
            assertThat(afterSubmit.errorMessage).contains("Invalid API token")
        }
    }

    @Test
    fun `submitting a token that fails with a network error keeps it stored`() = runTest(mainDispatcherRule.dispatcher) {
        server.enqueue(emptyResponse(500))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()

            viewModel.onTokenInputChange("new-token")
            awaitItem()
            viewModel.submitToken()

            var afterSubmit = awaitItem()
            while (afterSubmit.isSubmitting) afterSubmit = awaitItem()
            assertThat(afterSubmit.isAuthenticated).isFalse()
        }
        tokenRepository.tokenFlow.test { assertThat(awaitItem()).isEqualTo("new-token") }
    }

    private fun userJson() = """
        {
          "object": "user",
          "url": "https://api.wanikani.com/v2/user",
          "data": {
            "id": "abc-123",
            "username": "durtle_fan",
            "level": 12,
            "profile_url": "https://www.wanikani.com/users/durtle_fan",
            "started_at": "2020-01-01T00:00:00.000000Z"
          }
        }
    """.trimIndent()
}
