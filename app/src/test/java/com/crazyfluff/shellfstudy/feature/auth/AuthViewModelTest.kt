package com.crazyfluff.shellfstudy.feature.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.core.data.TokenRepository
import com.crazyfluff.shellfstudy.core.data.WaniKaniRepository
import com.crazyfluff.shellfstudy.fakes.FakeAssignmentDao
import com.crazyfluff.shellfstudy.fakes.FakeSubjectDao
import com.crazyfluff.shellfstudy.fakes.FakeTokenCipher
import com.crazyfluff.shellfstudy.fakes.buildTestApi
import com.crazyfluff.shellfstudy.fakes.emptyResponse
import com.crazyfluff.shellfstudy.fakes.jsonResponse
import com.google.common.truth.Truth.assertThat
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

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        tokenRepository = TokenRepository(dataStore, FakeTokenCipher())
        waniKaniRepository = WaniKaniRepository(
            api = buildTestApi(server.url("/").toString()),
            subjectDao = FakeSubjectDao(),
            assignmentDao = FakeAssignmentDao()
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun createViewModel() = AuthViewModel(tokenRepository, waniKaniRepository)

    @Test
    fun `with no stored token, finishes the check unauthenticated`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isCheckingStoredToken) state = awaitItem()
            assertThat(state.isAuthenticated).isFalse()
        }
    }

    @Test
    fun `with a valid stored token, auto-authenticates`() = runTest {
        tokenRepository.saveToken("stored-token")
        server.enqueue(jsonResponse(userJson()))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isCheckingStoredToken) state = awaitItem()
            assertThat(state.isAuthenticated).isTrue()
        }
    }

    @Test
    fun `with an invalid stored token, clears it and stays unauthenticated`() = runTest {
        tokenRepository.saveToken("stale-token")
        server.enqueue(emptyResponse(401))

        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isCheckingStoredToken) state = awaitItem()
            assertThat(state.isAuthenticated).isFalse()
        }
        tokenRepository.tokenFlow.test { assertThat(awaitItem()).isNull() }
    }

    @Test
    fun `submitting a blank token shows a validation error and makes no request`() = runTest {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isCheckingStoredToken) state = awaitItem()

            viewModel.submitToken()

            val afterSubmit = awaitItem()
            assertThat(afterSubmit.errorMessage).isNotNull()
            assertThat(afterSubmit.isAuthenticated).isFalse()
        }
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `submitting a valid token authenticates`() = runTest {
        server.enqueue(jsonResponse(userJson()))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isCheckingStoredToken) state = awaitItem()

            viewModel.onTokenInputChange("new-token")
            awaitItem() // input change emission
            viewModel.submitToken()

            var afterSubmit = awaitItem()
            while (afterSubmit.isSubmitting) afterSubmit = awaitItem()
            assertThat(afterSubmit.isAuthenticated).isTrue()
        }
    }

    @Test
    fun `submitting an invalid token shows an error and does not authenticate`() = runTest {
        server.enqueue(emptyResponse(401))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isCheckingStoredToken) state = awaitItem()

            viewModel.onTokenInputChange("bad-token")
            awaitItem()
            viewModel.submitToken()

            var afterSubmit = awaitItem()
            while (afterSubmit.isSubmitting) afterSubmit = awaitItem()
            assertThat(afterSubmit.isAuthenticated).isFalse()
            assertThat(afterSubmit.errorMessage).contains("Invalid API token")
        }
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
