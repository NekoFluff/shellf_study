package com.crazyfluff.shellfstudy.feature.splash

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.core.data.TokenRepository
import com.crazyfluff.shellfstudy.fakes.FakeNotificationCoordinator
import com.crazyfluff.shellfstudy.fakes.FakePitchAccentScrapeScheduler
import com.crazyfluff.shellfstudy.fakes.FakeSyncScheduler
import com.crazyfluff.shellfstudy.fakes.FakeTokenCipher
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SplashViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var tokenRepository: TokenRepository
    private lateinit var syncScheduler: FakeSyncScheduler
    private lateinit var pitchAccentScrapeScheduler: FakePitchAccentScrapeScheduler
    private lateinit var notificationCoordinator: FakeNotificationCoordinator

    @Before
    fun setUp() {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        tokenRepository = TokenRepository(dataStore, FakeTokenCipher())
        syncScheduler = FakeSyncScheduler()
        pitchAccentScrapeScheduler = FakePitchAccentScrapeScheduler()
        notificationCoordinator = FakeNotificationCoordinator()
    }

    private fun createViewModel() =
        SplashViewModel(tokenRepository, syncScheduler, pitchAccentScrapeScheduler, notificationCoordinator)

    @Test
    fun `with no stored token, routes to auth without touching background sync`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.destination == null) state = awaitItem()
            assertThat(state.destination).isEqualTo(SplashDestination.AUTH)
        }
        assertThat(syncScheduler.scheduleCallCount).isEqualTo(0)
        assertThat(notificationCoordinator.onLoginCallCount).isEqualTo(0)
    }

    @Test
    fun `with a stored token, routes to dashboard and schedules background sync without any network call`() = runTest {
        tokenRepository.saveToken("stored-token")
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.destination == null) state = awaitItem()
            assertThat(state.destination).isEqualTo(SplashDestination.DASHBOARD)
        }
        assertThat(syncScheduler.scheduleCallCount).isEqualTo(1)
        assertThat(pitchAccentScrapeScheduler.scheduleCallCount).isEqualTo(1)
        assertThat(notificationCoordinator.onLoginCallCount).isEqualTo(1)
    }
}
