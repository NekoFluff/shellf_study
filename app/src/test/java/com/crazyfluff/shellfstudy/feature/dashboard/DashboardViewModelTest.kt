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
import com.crazyfluff.shellfstudy.core.network.SubjectType
import com.crazyfluff.shellfstudy.fakes.FakeNotificationCoordinator
import com.crazyfluff.shellfstudy.fakes.FakePitchAccentScrapeScheduler
import com.crazyfluff.shellfstudy.fakes.FakeSyncScheduler
import com.crazyfluff.shellfstudy.fakes.FakeTokenCipher
import com.crazyfluff.shellfstudy.fakes.TestRepositories
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
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
    private lateinit var repositories: TestRepositories
    private lateinit var reviewSessionRepository: ReviewSessionRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var syncScheduler: FakeSyncScheduler
    private lateinit var pitchAccentScrapeScheduler: FakePitchAccentScrapeScheduler
    private lateinit var notificationCoordinator: FakeNotificationCoordinator

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        tokenRepository = TokenRepository(dataStore, FakeTokenCipher())
        repositories = buildTestRepositories(server.url("/").toString())
        reviewSessionRepository = ReviewSessionRepository(dataStore, Json { ignoreUnknownKeys = true })
        settingsRepository = SettingsRepository(dataStore)
        syncScheduler = FakeSyncScheduler()
        pitchAccentScrapeScheduler = FakePitchAccentScrapeScheduler()
        notificationCoordinator = FakeNotificationCoordinator()
    }

    private val viewModelStore = ViewModelStore()

    @After
    fun tearDown() {
        // DashboardViewModel.uiState is backed by a MutableStateFlow fed by several independent
        // viewModelScope collectors (settings, review-session, and repository-derived stats). In
        // production those collectors die with the ViewModel's own viewModelScope via onCleared(),
        // triggered by ViewModelStore.clear() on Activity/Fragment destruction. Nothing does that
        // automatically here, so routing creation through a real ViewModelStore lets us trigger the
        // same cleanup Android would, and draining MainDispatcherRule's scheduler afterwards forces
        // that cancellation to actually settle now — while the MockWebServer and temp DataStore file
        // are still alive — instead of resolving asynchronously after this test has ended, which can
        // otherwise surface as `UncaughtExceptionsBeforeTest` in whichever test runs next.
        viewModelStore.clear()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        server.shutdown()
    }

    private fun createViewModel(): DashboardViewModel {
        val factory = viewModelFactory {
            initializer {
                DashboardViewModel(
                    waniKaniRepository = repositories.waniKaniRepository,
                    tokenRepository = tokenRepository,
                    reviewSessionRepository = reviewSessionRepository,
                    settingsRepository = settingsRepository,
                    subjectRepository = repositories.subjectRepository,
                    assignmentRepository = repositories.assignmentRepository,
                    statsRepository = repositories.statsRepository,
                    syncOrchestrator = repositories.syncOrchestrator,
                    syncScheduler = syncScheduler,
                    pitchAccentScrapeScheduler = pitchAccentScrapeScheduler,
                    notificationCoordinator = notificationCoordinator
                )
            }
        }
        return ViewModelProvider(viewModelStore, factory)[DashboardViewModel::class.java]
    }

    /** Every non-user/summary resource defaults to an empty collection when a test doesn't care about it. */
    private fun dispatchByPath(
        userResponse: MockResponse,
        summaryResponse: MockResponse,
        assignmentsResponse: MockResponse = jsonResponse(emptyCollectionJson()),
        subjectsResponse: MockResponse = jsonResponse(emptyCollectionJson()),
        levelProgressionsResponse: MockResponse = jsonResponse(emptyCollectionJson())
    ) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/user") -> userResponse
                    path.startsWith("/summary") -> summaryResponse
                    path.startsWith("/assignments") -> assignmentsResponse
                    path.startsWith("/subjects") -> subjectsResponse
                    path.startsWith("/level_progressions") -> levelProgressionsResponse
                    else -> jsonResponse(emptyCollectionJson())
                }
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
            cancelAndIgnoreRemainingEvents()
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
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `logOut clears the stored token, cancels background sync, and marks state logged out`() = runTest {
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
            cancelAndIgnoreRemainingEvents()
        }

        tokenRepository.tokenFlow.test { assertThat(awaitItem()).isNull() }
        assertThat(syncScheduler.cancelCallCount).isEqualTo(1)
        assertThat(notificationCoordinator.onLogoutCallCount).isEqualTo(1)
    }

    @Test
    fun `loads lessons completed today and the default daily goal`() = runTest {
        dispatchByPath(
            jsonResponse(userJson()),
            jsonResponse(summaryJson()),
            assignmentsResponse = jsonResponse(startedTodayAssignmentsJson(count = 4))
        )
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            while (state.lessonsCompletedToday != 4) state = awaitItem()

            assertThat(state.lessonsCompletedToday).isEqualTo(4)
            assertThat(state.dailyLessonGoal).isEqualTo(15)
            cancelAndIgnoreRemainingEvents()
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
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loads level-up progress and days on current level`() = runTest {
        dispatchByPath(
            jsonResponse(userJson()),
            jsonResponse(summaryJson()),
            assignmentsResponse = jsonResponse(levelUpAssignmentsJson()),
            subjectsResponse = jsonResponse(levelUpSubjectsJson()),
            levelProgressionsResponse = jsonResponse(levelProgressionsJson())
        )
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            while (state.kanjiTotalForLevelUp == 0) state = awaitItem()

            assertThat(state.kanjiTotalForLevelUp).isEqualTo(2)
            assertThat(state.kanjiGuruedForLevelUp).isEqualTo(1)
            while (state.daysOnCurrentLevel == null) state = awaitItem()
            assertThat(state.daysOnCurrentLevel).isNotNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `browsing the level progress card to a different level leaves current-level stats untouched`() = runTest {
        dispatchByPath(
            jsonResponse(userJson()),
            jsonResponse(summaryJson()),
            assignmentsResponse = jsonResponse(levelUpAssignmentsJson(includeOtherLevel = true)),
            subjectsResponse = jsonResponse(levelUpSubjectsJson(includeOtherLevel = true))
        )
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.levelProgress?.level != 12) state = awaitItem()
            assertThat(state.kanjiTotalForLevelUp).isEqualTo(2)

            viewModel.onLevelProgressLevelChange(5)
            while (state.levelProgress?.level != 5) state = awaitItem()

            val kanjiAtLevel5 = state.levelProgress!!.breakdown.first { it.subjectType == SubjectType.KANJI }
            assertThat(kanjiAtLevel5.totalCount).isEqualTo(1)
            assertThat(kanjiAtLevel5.passedCount).isEqualTo(0)
            // Guru-for-levelup stats always track the level actually being studied, not the browsed one.
            assertThat(state.kanjiTotalForLevelUp).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `computes a completion projection from total subjects and items seen`() = runTest {
        dispatchByPath(
            jsonResponse(userJson()),
            jsonResponse(summaryJson()),
            assignmentsResponse = jsonResponse(startedTodayAssignmentsJson(count = 1)),
            subjectsResponse = jsonResponse(levelUpSubjectsJson())
        )
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.completionProjection?.totalItems != 2) state = awaitItem()

            val projection = state.completionProjection
            assertThat(projection.totalItems).isEqualTo(2)
            assertThat(projection.dailyPace).isEqualTo(15)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun emptyCollectionJson() = """
        {"object": "collection", "url": "https://api.wanikani.com/v2/x", "total_count": 0, "data": []}
    """.trimIndent()

    private fun startedTodayAssignmentsJson(count: Int) = """
        {
          "object": "collection",
          "url": "https://api.wanikani.com/v2/assignments",
          "total_count": $count,
          "data": [${(1..count).joinToString(",") { id ->
        """
                {
                  "id": $id, "object": "assignment", "url": "https://api.wanikani.com/v2/assignments/$id",
                  "data_updated_at": "2026-01-01T00:00:00.000000Z",
                  "data": {
                    "created_at": "2026-01-01T00:00:00.000000Z", "subject_id": $id, "subject_type": "kanji",
                    "srs_stage": 1, "started_at": "${java.time.Instant.now()}", "hidden": false
                  }
                }
            """.trimIndent()
    }}]
        }
    """.trimIndent()

    private fun levelUpAssignmentsJson(includeOtherLevel: Boolean = false) = """
        {
          "object": "collection",
          "url": "https://api.wanikani.com/v2/assignments",
          "total_count": 2,
          "data": [
            {
              "id": 301, "object": "assignment", "url": "https://api.wanikani.com/v2/assignments/301",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "2026-01-01T00:00:00.000000Z", "subject_id": 1, "subject_type": "kanji",
                "srs_stage": 5, "unlocked_at": "2026-01-01T00:00:00.000000Z", "hidden": false
              }
            },
            {
              "id": 302, "object": "assignment", "url": "https://api.wanikani.com/v2/assignments/302",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "2026-01-01T00:00:00.000000Z", "subject_id": 2, "subject_type": "kanji",
                "srs_stage": 2, "unlocked_at": "2026-01-01T00:00:00.000000Z", "hidden": false
              }
            }${
        if (!includeOtherLevel) "" else """,
            {
              "id": 303, "object": "assignment", "url": "https://api.wanikani.com/v2/assignments/303",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "2026-01-01T00:00:00.000000Z", "subject_id": 3, "subject_type": "kanji",
                "srs_stage": 1, "unlocked_at": "2026-01-01T00:00:00.000000Z", "hidden": false
              }
            }"""
    }
          ]
        }
    """.trimIndent()

    private fun levelUpSubjectsJson(includeOtherLevel: Boolean = false) = """
        {
          "object": "collection",
          "url": "https://api.wanikani.com/v2/subjects",
          "total_count": 2,
          "data": [
            {
              "id": 1, "object": "kanji", "url": "https://api.wanikani.com/v2/subjects/1",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "2020-01-01T00:00:00.000000Z", "level": 12, "slug": "一", "characters": "一",
                "meanings": [{"meaning": "One", "primary": true, "accepted_meaning": true}],
                "readings": [{"reading": "いち", "primary": true, "accepted_reading": true}]
              }
            },
            {
              "id": 2, "object": "kanji", "url": "https://api.wanikani.com/v2/subjects/2",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "2020-01-01T00:00:00.000000Z", "level": 12, "slug": "二", "characters": "二",
                "meanings": [{"meaning": "Two", "primary": true, "accepted_meaning": true}],
                "readings": [{"reading": "に", "primary": true, "accepted_reading": true}]
              }
            }${
        if (!includeOtherLevel) "" else """,
            {
              "id": 3, "object": "kanji", "url": "https://api.wanikani.com/v2/subjects/3",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "2020-01-01T00:00:00.000000Z", "level": 5, "slug": "三", "characters": "三",
                "meanings": [{"meaning": "Three", "primary": true, "accepted_meaning": true}],
                "readings": [{"reading": "さん", "primary": true, "accepted_reading": true}]
              }
            }"""
    }
          ]
        }
    """.trimIndent()

    private fun levelProgressionsJson() = """
        {
          "object": "collection",
          "url": "https://api.wanikani.com/v2/level_progressions",
          "total_count": 1,
          "data": [
            {
              "id": 1, "object": "level_progression", "url": "https://api.wanikani.com/v2/level_progressions/1",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "created_at": "2026-01-01T00:00:00.000000Z", "level": 12,
                "unlocked_at": "2026-01-01T00:00:00.000000Z", "started_at": "2026-01-01T00:00:00.000000Z"
              }
            }
          ]
        }
    """.trimIndent()

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
