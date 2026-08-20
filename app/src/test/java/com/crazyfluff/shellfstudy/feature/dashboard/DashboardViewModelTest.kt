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
import com.crazyfluff.shellfstudy.shared.feature.dashboard.DashboardBannerState
import com.crazyfluff.shellfstudy.shared.feature.dashboard.DashboardViewModel
import com.crazyfluff.shellfstudy.shared.data.AccountDataCleaner
import com.crazyfluff.shellfstudy.shared.data.DashboardCacheRepository
import com.crazyfluff.shellfstudy.shared.data.DashboardSyncCoordinator
import com.crazyfluff.shellfstudy.shared.data.LastSessionKind
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummary
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummaryRepository
import com.crazyfluff.shellfstudy.shared.data.LessonSessionRepository
import com.crazyfluff.shellfstudy.shared.data.LogoutCoordinator
import com.crazyfluff.shellfstudy.shared.data.OutboxRepository
import com.crazyfluff.shellfstudy.shared.data.PersistedItemProgress
import com.crazyfluff.shellfstudy.shared.data.PersistedLessonQuestion
import com.crazyfluff.shellfstudy.shared.data.PersistedLessonSession
import com.crazyfluff.shellfstudy.shared.data.PersistedQuestion
import com.crazyfluff.shellfstudy.shared.data.PersistedReviewSession
import com.crazyfluff.shellfstudy.shared.data.ReviewSessionRepository
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.TokenRepository
import com.crazyfluff.shellfstudy.shared.lifecycle.AppForegroundTracker
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import com.crazyfluff.shellfstudy.fakes.FakeFriendStatsDao
import com.crazyfluff.shellfstudy.fakes.FakeLevelProgressionDao
import com.crazyfluff.shellfstudy.fakes.FakeLifecycleOwner
import com.crazyfluff.shellfstudy.fakes.FakeNotificationCoordinator
import com.crazyfluff.shellfstudy.fakes.FakePitchAccentScrapeScheduler
import com.crazyfluff.shellfstudy.fakes.FakeReviewStatisticDao
import com.crazyfluff.shellfstudy.fakes.FakeSyncScheduler
import com.crazyfluff.shellfstudy.fakes.FakeTokenCipher
import com.crazyfluff.shellfstudy.fakes.TestRepositories
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.crazyfluff.shellfstudy.fakes.emptyResponse
import com.crazyfluff.shellfstudy.fakes.jsonResponse
import com.crazyfluff.shellfstudy.shared.data.FriendRepository
import com.crazyfluff.shellfstudy.shared.data.FriendStatsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
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
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var tokenRepository: TokenRepository
    private lateinit var repositories: TestRepositories
    private lateinit var reviewSessionRepository: ReviewSessionRepository
    private lateinit var lessonSessionRepository: LessonSessionRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var dashboardCacheRepository: DashboardCacheRepository
    private lateinit var outboxRepository: OutboxRepository
    private lateinit var syncScheduler: FakeSyncScheduler
    private lateinit var pitchAccentScrapeScheduler: FakePitchAccentScrapeScheduler
    private lateinit var notificationCoordinator: FakeNotificationCoordinator
    private lateinit var appForegroundTracker: AppForegroundTracker

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(mainDispatcherRule.dispatcher + SupervisorJob()),
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        tokenRepository = TokenRepository(dataStore, FakeTokenCipher())
        repositories = buildTestRepositories(server.url("/").toString(), defaultDispatcher = mainDispatcherRule.dispatcher)
        outboxRepository = OutboxRepository(repositories.outboxDao, repositories.outboxSyncScheduler, dataStore)
        reviewSessionRepository = ReviewSessionRepository(dataStore, Json { ignoreUnknownKeys = true })
        lessonSessionRepository = LessonSessionRepository(dataStore, Json { ignoreUnknownKeys = true })
        settingsRepository = SettingsRepository(dataStore)
        dashboardCacheRepository = DashboardCacheRepository(dataStore)
        syncScheduler = FakeSyncScheduler()
        pitchAccentScrapeScheduler = FakePitchAccentScrapeScheduler()
        notificationCoordinator = FakeNotificationCoordinator()
        appForegroundTracker = AppForegroundTracker()
    }

    private val viewModelStore = ViewModelStore()

    @After
    fun tearDown() {
        // DashboardViewModel.uiState is a combine() chain (settings, review-session, and
        // repository-derived stats) wrapped in stateIn(WhileSubscribed(...)), kept alive here by
        // Turbine's own active collection in each test. In production that subscription — and
        // everything upstream of it — dies with the ViewModel's own viewModelScope via
        // onCleared(), triggered by ViewModelStore.clear() on Activity/Fragment destruction.
        // Nothing does that automatically here, so routing creation through a real ViewModelStore
        // lets us trigger the same cleanup Android would, and draining MainDispatcherRule's
        // scheduler afterwards forces that cancellation to actually settle now — while the
        // MockWebServer and temp DataStore file are still alive — instead of resolving
        // asynchronously after this test has ended, which can otherwise surface as
        // `UncaughtExceptionsBeforeTest` in whichever test runs next.
        viewModelStore.clear()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        server.shutdown()
    }

    private fun createViewModel(): DashboardViewModel {
        val json = Json { ignoreUnknownKeys = true }
        val friendRepository = FriendRepository(dataStore, json, FakeTokenCipher())
        val friendStatsRepository = FriendStatsRepository(
            friendRepository = friendRepository,
            friendStatsDao = FakeFriendStatsDao(),
            json = json,
            selfAssignmentDao = repositories.assignmentDao,
            selfReviewStatisticDao = FakeReviewStatisticDao(),
            selfLevelProgressionDao = FakeLevelProgressionDao(),
            defaultDispatcher = mainDispatcherRule.dispatcher
        )
        val accountDataCleaner = AccountDataCleaner(
            assignmentDao = repositories.assignmentDao,
            reviewStatisticDao = repositories.reviewStatisticDao,
            levelProgressionDao = FakeLevelProgressionDao(),
            syncStateDao = repositories.syncStateDao,
            outboxDao = repositories.outboxDao,
            studyActivityDao = repositories.studyActivityDao,
            outboxRepository = outboxRepository,
            dashboardCacheRepository = dashboardCacheRepository,
            lastSessionSummaryRepository = LastSessionSummaryRepository(dataStore, json),
            reviewSessionRepository = reviewSessionRepository,
            lessonSessionRepository = lessonSessionRepository
        )
        val logoutCoordinator = LogoutCoordinator(
            tokenRepository = tokenRepository,
            syncScheduler = syncScheduler,
            pitchAccentScrapeScheduler = pitchAccentScrapeScheduler,
            notificationCoordinator = notificationCoordinator,
            accountDataCleaner = accountDataCleaner
        )
        val dashboardSyncCoordinator = DashboardSyncCoordinator(
            waniKaniRepository = repositories.waniKaniRepository,
            syncOrchestrator = repositories.syncOrchestrator,
            dashboardCacheRepository = dashboardCacheRepository
        )
        val factory = viewModelFactory {
            initializer {
                DashboardViewModel(
                    reviewSessionRepository = reviewSessionRepository,
                    lessonSessionRepository = lessonSessionRepository,
                    settingsRepository = settingsRepository,
                    subjectRepository = repositories.subjectRepository,
                    assignmentRepository = repositories.assignmentRepository,
                    statsRepository = repositories.statsRepository,
                    outboxRepository = outboxRepository,
                    outboxSyncScheduler = repositories.outboxSyncScheduler,
                    friendStatsRepository = friendStatsRepository,
                    logoutCoordinator = logoutCoordinator,
                    dashboardSyncCoordinator = dashboardSyncCoordinator,
                    lastSessionSummaryRepository = LastSessionSummaryRepository(dataStore, json),
                    appForegroundTracker = appForegroundTracker
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
    fun `loads user and summary when the dashboard first appears`() = runTest(mainDispatcherRule.dispatcher) {
        dispatchByPath(jsonResponse(userJson()), jsonResponse(summaryJson()))
        val viewModel = createViewModel()
        // Simulates DashboardRoute's LaunchedEffect(Unit) — construction alone (init{} now only
        // seeds from cache) no longer triggers a network fetch on its own.
        viewModel.onDashboardResumed()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isRefreshing) state = awaitItem()

            assertThat(state.username).isEqualTo("durtle_fan")
            assertThat(state.level).isEqualTo(12)
            assertThat(state.lessonCount).isEqualTo(2)
            assertThat(state.reviewCount).isEqualTo(3)
            assertThat(state.errorMessage).isNull()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repositories.outboxSyncScheduler.immediateRequestCount).isAtLeast(1)
    }

    @Test
    fun `the first dashboard appearance fetches user and summary exactly once, not twice`() = runTest(mainDispatcherRule.dispatcher) {
        dispatchByPath(jsonResponse(userJson()), jsonResponse(summaryJson()))
        val viewModel = createViewModel()
        viewModel.onDashboardResumed()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isRefreshing) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        // Previously init{} launched its own refresh() racing against onDashboardResumed(), so the
        // very first appearance fetched user/summary twice. Draining every recorded request and
        // counting by path (rather than server.requestCount, which also counts sync's
        // assignments/subjects/level_progressions calls) confirms that's fixed.
        val requests = generateSequence { server.takeRequest(0, java.util.concurrent.TimeUnit.MILLISECONDS) }.toList()
        assertThat(requests.count { it.path.orEmpty().startsWith("/user") }).isEqualTo(1)
        assertThat(requests.count { it.path.orEmpty().startsWith("/summary") }).isEqualTo(1)
    }

    @Test
    fun `resyncs when the app returns to foreground, not just on the dashboard's first appearance`() = runTest(mainDispatcherRule.dispatcher) {
        // DashboardViewModel survives ordinary Review/Lesson navigation, so DashboardRoute's
        // LaunchedEffect(Unit) only re-fires onDashboardResumed() on true cold start. Backgrounding
        // and foregrounding the app (home button, app switcher) while still on the Dashboard route
        // doesn't recompose that LaunchedEffect at all — the appForegroundTracker collector wired
        // in init{} is what has to pick up the slack. This drives that collector directly (without
        // a preceding onDashboardResumed() call) since it's the mechanism under test here; the
        // hasCompletedInitialSync escalation itself is already covered by the "first appearance"
        // tests above.
        dispatchByPath(jsonResponse(userJson()), jsonResponse(summaryJson()))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()

            appForegroundTracker.onStop(FakeLifecycleOwner)
            appForegroundTracker.onStart(FakeLifecycleOwner)

            while (state.isRefreshing) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(repositories.outboxSyncScheduler.immediateRequestCount).isAtLeast(1)
        val requests = generateSequence { server.takeRequest(0, java.util.concurrent.TimeUnit.MILLISECONDS) }.toList()
        assertThat(requests.count { it.path.orEmpty().startsWith("/user") }).isAtLeast(1)
        assertThat(requests.count { it.path.orEmpty().startsWith("/summary") }).isAtLeast(1)
    }

    @Test
    fun `seeds cached username and counts before the network refresh resolves, then updates them once it does`() = runTest(mainDispatcherRule.dispatcher) {
        dashboardCacheRepository.save(
            username = "cached_user", level = 1, lessonCount = 9, reviewCount = 9, syncedAtMillis = 1_000L
        )
        dispatchByPath(jsonResponse(userJson()), jsonResponse(summaryJson()))
        val viewModel = createViewModel()
        viewModel.onDashboardResumed()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.username == null) state = awaitItem()
            assertThat(state.username).isEqualTo("cached_user")
            assertThat(state.lastSyncedAtMillis).isEqualTo(1_000L)
            assertThat(state.isRefreshing).isTrue()

            while (state.isRefreshing) state = awaitItem()
            assertThat(state.username).isEqualTo("durtle_fan")
            assertThat(state.lastSyncedAtMillis).isNotEqualTo(1_000L)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `falls back to cached content and flags offline, instead of the full error screen, when a refresh fails with content already cached`() = runTest(mainDispatcherRule.dispatcher) {
        dashboardCacheRepository.save(
            username = "cached_user", level = 5, lessonCount = 3, reviewCount = 7, syncedAtMillis = 1_000L
        )
        dispatchByPath(emptyResponse(500), jsonResponse(summaryJson()))
        val viewModel = createViewModel()
        viewModel.onDashboardResumed()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isRefreshing) state = awaitItem()

            assertThat(state.isOffline).isTrue()
            assertThat(state.errorMessage).isNull()
            assertThat(state.username).isEqualTo("cached_user")
            assertThat(state.lessonCount).isEqualTo(3)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `writes the fetched summary to the cache repository on a successful refresh`() = runTest(mainDispatcherRule.dispatcher) {
        dispatchByPath(jsonResponse(userJson()), jsonResponse(summaryJson()))
        val viewModel = createViewModel()
        viewModel.onDashboardResumed()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isRefreshing) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        dashboardCacheRepository.cachedSummary.test {
            val summary = awaitItem()
            assertThat(summary?.username).isEqualTo("durtle_fan")
            assertThat(summary?.level).isEqualTo(12)
            assertThat(summary?.lessonCount).isEqualTo(2)
            assertThat(summary?.reviewCount).isEqualTo(3)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shows an error message and keeps the token when the user fetch fails with a network error`() = runTest(mainDispatcherRule.dispatcher) {
        dispatchByPath(emptyResponse(500), jsonResponse(summaryJson()))
        tokenRepository.saveToken("some-token")
        val viewModel = createViewModel()
        viewModel.onDashboardResumed()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isRefreshing) state = awaitItem()
            assertThat(state.errorMessage).isNotNull()
            assertThat(state.isLoggedOut).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
        tokenRepository.tokenFlow.test { assertThat(awaitItem()).isEqualTo("some-token") }
    }

    @Test
    fun `a confirmed 401 on the user fetch clears the token and returns to the login flow`() = runTest(mainDispatcherRule.dispatcher) {
        dispatchByPath(emptyResponse(401), jsonResponse(summaryJson()))
        tokenRepository.saveToken("stale-token")
        val viewModel = createViewModel()
        viewModel.onDashboardResumed()

        viewModel.uiState.test {
            var state = awaitItem()
            while (!state.isLoggedOut) state = awaitItem()
            assertThat(state.isLoggedOut).isTrue()
            cancelAndIgnoreRemainingEvents()
        }

        tokenRepository.tokenFlow.test { assertThat(awaitItem()).isNull() }
        assertThat(syncScheduler.cancelCallCount).isEqualTo(1)
        assertThat(pitchAccentScrapeScheduler.cancelCallCount).isEqualTo(1)
        assertThat(notificationCoordinator.onLogoutCallCount).isEqualTo(1)
    }

    @Test
    fun `a confirmed 401 discovered on onDashboardResumed also logs out, not just isOffline`() = runTest(mainDispatcherRule.dispatcher) {
        dispatchByPath(jsonResponse(userJson()), jsonResponse(summaryJson()))
        tokenRepository.saveToken("stale-token")
        val viewModel = createViewModel()
        viewModel.onDashboardResumed()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isRefreshing) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        // A token revoked after the initial load — the resume path (not just manual refresh) must
        // also detect this rather than falling through to the generic isOffline treatment.
        dispatchByPath(emptyResponse(401), jsonResponse(summaryJson()))

        viewModel.uiState.test {
            viewModel.onDashboardResumed()
            var state = awaitItem()
            while (!state.isLoggedOut) state = awaitItem()
            assertThat(state.isLoggedOut).isTrue()
            assertThat(state.isOffline).isFalse()
            cancelAndIgnoreRemainingEvents()
        }

        tokenRepository.tokenFlow.test { assertThat(awaitItem()).isNull() }
        assertThat(syncScheduler.cancelCallCount).isEqualTo(1)
        assertThat(pitchAccentScrapeScheduler.cancelCallCount).isEqualTo(1)
        assertThat(notificationCoordinator.onLogoutCallCount).isEqualTo(1)
    }

    @Test
    fun `logOut clears the stored token, cancels background sync, and marks state logged out`() = runTest(mainDispatcherRule.dispatcher) {
        dispatchByPath(jsonResponse(userJson()), jsonResponse(summaryJson()))
        tokenRepository.saveToken("some-token")
        val viewModel = createViewModel()
        viewModel.onDashboardResumed()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isRefreshing) state = awaitItem()

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

    private val sampleReviewSession = PersistedReviewSession(
        queue = listOf(PersistedQuestion(assignmentId = 1, questionType = "MEANING")),
        progress = listOf(
            PersistedItemProgress(
                assignmentId = 1,
                meaningDone = false,
                readingDone = false,
                hadIncorrectMeaning = false,
                hadIncorrectReading = false
            )
        ),
        totalQuestions = 1
    )

    private val sampleLessonSession = PersistedLessonSession(
        quizQueue = listOf(PersistedLessonQuestion(assignmentId = 1, questionType = "MEANING")),
        totalQuizCount = 1
    )

    @Test
    fun `hasActiveReviewSession and hasActiveLessonSession reflect their repositories independently`() = runTest(mainDispatcherRule.dispatcher) {
        dispatchByPath(jsonResponse(userJson()), jsonResponse(summaryJson()))
        reviewSessionRepository.save(sampleReviewSession)
        val viewModel = createViewModel()
        viewModel.onDashboardResumed()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isRefreshing || !state.hasActiveReviewSession) state = awaitItem()
            assertThat(state.hasActiveReviewSession).isTrue()
            assertThat(state.hasActiveLessonSession).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hasLastSessionSummary reflects whether a last session summary has been persisted`() = runTest(mainDispatcherRule.dispatcher) {
        dispatchByPath(jsonResponse(userJson()), jsonResponse(summaryJson()))
        val lastSessionSummaryRepository = LastSessionSummaryRepository(dataStore, Json { ignoreUnknownKeys = true })
        lastSessionSummaryRepository.save(
            LastSessionSummary(
                kind = LastSessionKind.REVIEW,
                itemsCount = 3,
                correctFirstTry = 2,
                totalElapsedMs = 30_000,
                averageTimePerItemMs = 10_000,
                slowestAnswers = emptyList(),
                missedItems = emptyList(),
                completedAtMillis = 1_000L
            )
        )
        val viewModel = createViewModel()
        viewModel.onDashboardResumed()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isRefreshing || !state.hasLastSessionSummary) state = awaitItem()
            assertThat(state.hasLastSessionSummary).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `abandonReviewSession clears only the review session repository`() = runTest(mainDispatcherRule.dispatcher) {
        dispatchByPath(jsonResponse(userJson()), jsonResponse(summaryJson()))
        reviewSessionRepository.save(sampleReviewSession)
        lessonSessionRepository.save(sampleLessonSession)
        val viewModel = createViewModel()
        viewModel.onDashboardResumed()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isRefreshing || !state.hasActiveReviewSession) state = awaitItem()

            viewModel.abandonReviewSession()
            var afterAbandon = awaitItem()
            while (afterAbandon.hasActiveReviewSession) afterAbandon = awaitItem()
            assertThat(afterAbandon.hasActiveReviewSession).isFalse()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(reviewSessionRepository.load()).isNull()
        assertThat(lessonSessionRepository.load()).isNotNull()
    }

    @Test
    fun `abandonLessonSession clears only the lesson session repository`() = runTest(mainDispatcherRule.dispatcher) {
        dispatchByPath(jsonResponse(userJson()), jsonResponse(summaryJson()))
        reviewSessionRepository.save(sampleReviewSession)
        lessonSessionRepository.save(sampleLessonSession)
        val viewModel = createViewModel()
        viewModel.onDashboardResumed()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isRefreshing || !state.hasActiveLessonSession) state = awaitItem()

            viewModel.abandonLessonSession()
            var afterAbandon = awaitItem()
            while (afterAbandon.hasActiveLessonSession) afterAbandon = awaitItem()
            assertThat(afterAbandon.hasActiveLessonSession).isFalse()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(lessonSessionRepository.load()).isNull()
        assertThat(reviewSessionRepository.load()).isNotNull()
    }

    @Test
    fun `loads lessons completed today and the default daily goal`() = runTest(mainDispatcherRule.dispatcher) {
        dispatchByPath(
            jsonResponse(userJson()),
            jsonResponse(summaryJson()),
            assignmentsResponse = jsonResponse(startedTodayAssignmentsJson(count = 4))
        )
        val viewModel = createViewModel()
        viewModel.onDashboardResumed()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isRefreshing) state = awaitItem()
            while (state.lessonsCompletedToday != 4) state = awaitItem()

            assertThat(state.lessonsCompletedToday).isEqualTo(4)
            assertThat(state.dailyLessonGoal).isEqualTo(15)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reflects a custom daily lesson goal from settings`() = runTest(mainDispatcherRule.dispatcher) {
        dispatchByPath(jsonResponse(userJson()), jsonResponse(summaryJson()))
        settingsRepository.setDailyLessonGoal(5)
        val viewModel = createViewModel()
        viewModel.onDashboardResumed()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isRefreshing || state.dailyLessonGoal != 5) state = awaitItem()
            assertThat(state.dailyLessonGoal).isEqualTo(5)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loads level-up progress and days on current level`() = runTest(mainDispatcherRule.dispatcher) {
        dispatchByPath(
            jsonResponse(userJson()),
            jsonResponse(summaryJson()),
            assignmentsResponse = jsonResponse(levelUpAssignmentsJson()),
            subjectsResponse = jsonResponse(levelUpSubjectsJson()),
            levelProgressionsResponse = jsonResponse(levelProgressionsJson())
        )
        val viewModel = createViewModel()
        viewModel.onDashboardResumed()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isRefreshing) state = awaitItem()
            while ((state.levelUpProgress?.kanjiTotal ?: 0) == 0) state = awaitItem()

            assertThat(state.levelUpProgress?.kanjiTotal).isEqualTo(2)
            assertThat(state.levelUpProgress?.kanjiGuruedOrHigher).isEqualTo(1)
            while (state.daysOnCurrentLevel == null) state = awaitItem()
            assertThat(state.daysOnCurrentLevel).isNotNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `browsing the level progress card to a different level leaves current-level stats untouched`() = runTest(mainDispatcherRule.dispatcher) {
        dispatchByPath(
            jsonResponse(userJson()),
            jsonResponse(summaryJson()),
            assignmentsResponse = jsonResponse(levelUpAssignmentsJson(includeOtherLevel = true)),
            subjectsResponse = jsonResponse(levelUpSubjectsJson(includeOtherLevel = true))
        )
        val viewModel = createViewModel()
        viewModel.onDashboardResumed()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.levelProgress?.level != 12) state = awaitItem()
            assertThat(state.levelUpProgress?.kanjiTotal).isEqualTo(2)

            viewModel.onLevelProgressLevelChange(5)
            while (state.levelProgress?.level != 5) state = awaitItem()

            val kanjiAtLevel5 = state.levelProgress!!.breakdown.first { it.subjectType == SubjectType.KANJI }
            assertThat(kanjiAtLevel5.totalCount).isEqualTo(1)
            assertThat(kanjiAtLevel5.passedCount).isEqualTo(0)
            // Guru-for-levelup stats always track the level actually being studied, not the browsed one.
            assertThat(state.levelUpProgress?.kanjiTotal).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `computes a completion projection from total subjects and items seen`() = runTest(mainDispatcherRule.dispatcher) {
        dispatchByPath(
            jsonResponse(userJson()),
            jsonResponse(summaryJson()),
            assignmentsResponse = jsonResponse(startedTodayAssignmentsJson(count = 1)),
            subjectsResponse = jsonResponse(levelUpSubjectsJson())
        )
        val viewModel = createViewModel()
        viewModel.onDashboardResumed()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.completionProjection?.totalItems != 2) state = awaitItem()

            val projection = state.completionProjection!!
            assertThat(projection.totalItems).isEqualTo(2)
            assertThat(projection.dailyPace).isEqualTo(15)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `bannerState is PendingSync with the correct count when there are outbox rows`() = runTest(mainDispatcherRule.dispatcher) {
        dispatchByPath(jsonResponse(userJson()), jsonResponse(summaryJson()))
        // Two pending rows seeded before ViewModel observes, so the flow starts non-zero.
        repositories.outboxDao.insertReviewSubmission(
            com.crazyfluff.shellfstudy.shared.database.outbox.PendingReviewSubmissionEntity(
                assignmentId = 1, subjectId = 1, incorrectMeaningAnswers = 0, incorrectReadingAnswers = 0,
                gradedAt = "2026-01-01T00:00:00.000000Z"
            )
        )
        repositories.outboxDao.insertReviewSubmission(
            com.crazyfluff.shellfstudy.shared.database.outbox.PendingReviewSubmissionEntity(
                assignmentId = 2, subjectId = 2, incorrectMeaningAnswers = 1, incorrectReadingAnswers = 0,
                gradedAt = "2026-01-01T00:00:00.000000Z"
            )
        )
        val viewModel = createViewModel()
        viewModel.onDashboardResumed()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.pendingSyncCount == 0) state = awaitItem()

            assertThat(state.bannerState).isEqualTo(DashboardBannerState.PendingSync(2))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `bannerState is SyncBlockedOnAuth when the outbox is blocked on an auth error`() = runTest(mainDispatcherRule.dispatcher) {
        dispatchByPath(jsonResponse(userJson()), jsonResponse(summaryJson()))
        outboxRepository.setBlockedOnAuth(true)
        val viewModel = createViewModel()
        viewModel.onDashboardResumed()

        viewModel.uiState.test {
            var state = awaitItem()
            while (!state.syncBlockedOnAuth) state = awaitItem()

            assertThat(state.bannerState).isEqualTo(DashboardBannerState.SyncBlockedOnAuth)
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
