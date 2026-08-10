package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.core.data.ReviewSessionRepository
import com.crazyfluff.shellfstudy.core.data.SettingsRepository
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
import kotlinx.serialization.json.Json
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var tokenRepository: TokenRepository
    private lateinit var waniKaniRepository: WaniKaniRepository
    private lateinit var reviewSessionRepository: ReviewSessionRepository
    private lateinit var settingsRepository: SettingsRepository

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
        reviewSessionRepository = ReviewSessionRepository(dataStore, Json { ignoreUnknownKeys = true })
        settingsRepository = SettingsRepository(dataStore)
    }

    private val viewModelStore = ViewModelStore()

    @After
    fun tearDown() {
        // DashboardViewModel keeps two collectors on infinite Flows alive for its whole lifetime
        // (hasActiveSession, settings) — in production those die with the ViewModel's own
        // viewModelScope via onCleared(), triggered by ViewModelStore.clear() on Activity/Fragment
        // destruction. Nothing does that automatically here, so a collector left running past this
        // test can crash a *later* test when it resumes after MainDispatcherRule has reset
        // Dispatchers.Main. Routing creation through a real ViewModelStore lets us trigger the same
        // cleanup Android would.
        viewModelStore.clear()
        server.shutdown()
    }

    private fun createViewModel(): DashboardViewModel {
        val factory = viewModelFactory {
            initializer { DashboardViewModel(waniKaniRepository, tokenRepository, reviewSessionRepository, settingsRepository) }
        }
        return ViewModelProvider(viewModelStore, factory)[DashboardViewModel::class.java]
    }

    /** [lessonsTodayResponse] defaults to an empty assignments page (0 lessons completed today). */
    private fun dispatchByPath(
        userResponse: MockResponse,
        summaryResponse: MockResponse,
        lessonsTodayResponse: MockResponse = jsonResponse(emptyAssignmentsJson())
    ) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path.orEmpty().startsWith("/user") -> userResponse
                request.path.orEmpty().startsWith("/summary") -> summaryResponse
                else -> lessonsTodayResponse
            }
        }
    }

    @Test
    fun `loads user and summary on init`() = runTest {
        dispatchByPath(jsonResponse(userJson()), jsonResponse(summaryJson()))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            assertThat(state.username).isEqualTo("durtle_fan")
            assertThat(state.level).isEqualTo(12)
            assertThat(state.lessonCount).isEqualTo(2)
            assertThat(state.reviewCount).isEqualTo(3)
            assertThat(state.errorMessage).isNull()
        }
    }

    @Test
    fun `shows an error message when the user fetch fails`() = runTest {
        dispatchByPath(emptyResponse(500), jsonResponse(summaryJson()))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.errorMessage).isNotNull()
        }
    }

    @Test
    fun `logOut clears the stored token and marks state logged out`() = runTest {
        dispatchByPath(jsonResponse(userJson()), jsonResponse(summaryJson()))
        tokenRepository.saveToken("some-token")
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            viewModel.logOut()
            var afterLogout = awaitItem()
            while (!afterLogout.isLoggedOut) afterLogout = awaitItem()
            assertThat(afterLogout.isLoggedOut).isTrue()
        }

        tokenRepository.tokenFlow.test { assertThat(awaitItem()).isNull() }
    }

    @Test
    fun `loads lessons completed today and the default daily goal`() = runTest {
        dispatchByPath(
            jsonResponse(userJson()),
            jsonResponse(summaryJson()),
            jsonResponse(assignmentsPageJson(totalCount = 4))
        )
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            assertThat(state.lessonsCompletedToday).isEqualTo(4)
            assertThat(state.dailyLessonGoal).isEqualTo(15)
        }
    }

    @Test
    fun `reflects a custom daily lesson goal from settings`() = runTest {
        dispatchByPath(jsonResponse(userJson()), jsonResponse(summaryJson()))
        settingsRepository.setDailyLessonGoal(5)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading || state.dailyLessonGoal != 5) state = awaitItem()
            assertThat(state.dailyLessonGoal).isEqualTo(5)
        }
    }

    private fun assignmentsPageJson(totalCount: Int) = """
        {
          "object": "collection",
          "url": "https://api.wanikani.com/v2/assignments",
          "total_count": $totalCount,
          "data": []
        }
    """.trimIndent()

    private fun emptyAssignmentsJson() = assignmentsPageJson(totalCount = 0)

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

    private fun summaryJson() = """
        {
          "object": "report",
          "url": "https://api.wanikani.com/v2/summary",
          "data": {
            "lessons": [{"available_at": "2026-01-01T00:00:00.000000Z", "subject_ids": [1, 2]}],
            "reviews": [{"available_at": "2026-01-01T00:00:00.000000Z", "subject_ids": [3, 4, 5]}]
          }
        }
    """.trimIndent()
}
