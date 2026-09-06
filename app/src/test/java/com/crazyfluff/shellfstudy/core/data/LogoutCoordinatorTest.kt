package com.crazyfluff.shellfstudy.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.fakes.FakeAssignmentDao
import com.crazyfluff.shellfstudy.fakes.FakeLevelProgressionDao
import com.crazyfluff.shellfstudy.fakes.FakeNotificationCoordinator
import com.crazyfluff.shellfstudy.fakes.FakeOutboxDao
import com.crazyfluff.shellfstudy.fakes.FakeOutboxSyncScheduler
import com.crazyfluff.shellfstudy.fakes.FakePitchAccentScrapeScheduler
import com.crazyfluff.shellfstudy.fakes.FakeReviewStatisticDao
import com.crazyfluff.shellfstudy.fakes.FakeSrsSystemDao
import com.crazyfluff.shellfstudy.fakes.FakeStudyActivityDao
import com.crazyfluff.shellfstudy.fakes.FakeSubjectDao
import com.crazyfluff.shellfstudy.fakes.FakeSyncScheduler
import com.crazyfluff.shellfstudy.fakes.FakeSyncStateDao
import com.crazyfluff.shellfstudy.fakes.FakeTokenCipher
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.crazyfluff.shellfstudy.shared.data.AccountDataCleaner
import com.crazyfluff.shellfstudy.shared.data.DashboardCacheRepository
import com.crazyfluff.shellfstudy.shared.data.FriendRepository
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummaryRepository
import com.crazyfluff.shellfstudy.shared.data.LessonSessionRepository
import com.crazyfluff.shellfstudy.shared.data.LogoutCoordinator
import com.crazyfluff.shellfstudy.shared.data.OutboxRepository
import com.crazyfluff.shellfstudy.shared.data.PersistedQuestion
import com.crazyfluff.shellfstudy.shared.data.PersistedReviewSession
import com.crazyfluff.shellfstudy.shared.data.ReviewSessionRepository
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.TokenRepository
import com.crazyfluff.shellfstudy.shared.database.AssignmentEntity
import com.crazyfluff.shellfstudy.shared.database.SrsSystemEntity
import com.crazyfluff.shellfstudy.shared.database.SubjectEntity
import com.crazyfluff.shellfstudy.shared.network.MeaningData
import com.crazyfluff.shellfstudy.shared.network.ReadingData
import com.crazyfluff.shellfstudy.shared.network.SrsStageData
import com.crazyfluff.shellfstudy.shared.session.LessonSessionController
import com.crazyfluff.shellfstudy.shared.session.ReviewSessionController
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LogoutCoordinatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var json: Json
    private lateinit var tokenRepository: TokenRepository
    private lateinit var syncScheduler: FakeSyncScheduler
    private lateinit var pitchAccentScrapeScheduler: FakePitchAccentScrapeScheduler
    private lateinit var notificationCoordinator: FakeNotificationCoordinator
    private lateinit var assignmentDao: FakeAssignmentDao
    private lateinit var syncStateDao: FakeSyncStateDao
    private lateinit var outboxDao: FakeOutboxDao
    private lateinit var studyActivityDao: FakeStudyActivityDao
    private lateinit var outboxRepository: OutboxRepository
    private lateinit var reviewSessionRepository: ReviewSessionRepository
    private lateinit var lessonSessionRepository: LessonSessionRepository
    private lateinit var subjectDao: FakeSubjectDao
    private lateinit var srsSystemDao: FakeSrsSystemDao
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var friendRepository: FriendRepository
    private lateinit var logoutCoordinator: LogoutCoordinator

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        json = Json { ignoreUnknownKeys = true }
        tokenRepository = TokenRepository(dataStore, FakeTokenCipher())
        syncScheduler = FakeSyncScheduler()
        pitchAccentScrapeScheduler = FakePitchAccentScrapeScheduler()
        notificationCoordinator = FakeNotificationCoordinator()

        assignmentDao = FakeAssignmentDao()
        syncStateDao = FakeSyncStateDao()
        outboxDao = FakeOutboxDao()
        studyActivityDao = FakeStudyActivityDao()
        outboxRepository = OutboxRepository(outboxDao, FakeOutboxSyncScheduler(), dataStore)
        reviewSessionRepository = ReviewSessionRepository(dataStore, json)
        lessonSessionRepository = LessonSessionRepository(dataStore, json)
        val reviewSessionController = ReviewSessionController(CoroutineScope(SupervisorJob()), reviewSessionRepository)
        val lessonSessionController = LessonSessionController(CoroutineScope(SupervisorJob()), lessonSessionRepository)

        subjectDao = FakeSubjectDao()
        srsSystemDao = FakeSrsSystemDao()
        settingsRepository = SettingsRepository(dataStore)
        friendRepository = FriendRepository(dataStore, json, FakeTokenCipher())

        val accountDataCleaner = AccountDataCleaner(
            assignmentDao = assignmentDao,
            reviewStatisticDao = FakeReviewStatisticDao(),
            levelProgressionDao = FakeLevelProgressionDao(),
            syncStateDao = syncStateDao,
            outboxDao = outboxDao,
            studyActivityDao = studyActivityDao,
            outboxRepository = outboxRepository,
            dashboardCacheRepository = DashboardCacheRepository(dataStore),
            lastSessionSummaryRepository = LastSessionSummaryRepository(dataStore, json),
            reviewSessionController = reviewSessionController,
            lessonSessionController = lessonSessionController
        )
        logoutCoordinator = LogoutCoordinator(
            tokenRepository = tokenRepository,
            syncScheduler = syncScheduler,
            pitchAccentScrapeScheduler = pitchAccentScrapeScheduler,
            notificationCoordinator = notificationCoordinator,
            accountDataCleaner = accountDataCleaner
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `logout clears the token, cancels scheduled work, resets notifications, and wipes account-scoped stores`() = runTest {
        tokenRepository.saveToken("some-token")
        assignmentDao.upsertAll(listOf(AssignmentEntity(id = 1, subjectId = 1, subjectType = "kanji", srsStage = 1, createdAt = "2026-08-01T00:00:00Z", hidden = false)))
        reviewSessionRepository.save(PersistedReviewSession(queue = listOf(PersistedQuestion(1, "MEANING")), progress = emptyList(), totalQuestions = 1))

        logoutCoordinator.logout()

        tokenRepository.tokenFlow.test { assertThat(awaitItem()).isNull() }
        assertThat(syncScheduler.cancelCallCount).isEqualTo(1)
        assertThat(pitchAccentScrapeScheduler.cancelCallCount).isEqualTo(1)
        assertThat(notificationCoordinator.onLogoutCallCount).isEqualTo(1)
        assertThat(assignmentDao.getById(1)).isNull()
        assertThat(reviewSessionRepository.load()).isNull()
    }

    @Test
    fun `logout leaves friends, settings, and shared subject content untouched`() = runTest {
        friendRepository.addFriend(nickname = "rival", plainToken = "their-token")
        settingsRepository.setDailyLessonGoal(15)
        subjectDao.upsertAll(
            listOf(
                SubjectEntity(
                    id = 1,
                    subjectType = "vocabulary",
                    level = 1,
                    slug = "水",
                    characters = "水",
                    meanings = listOf(MeaningData(meaning = "Water", primary = true)),
                    readings = listOf(ReadingData(reading = "みず", primary = true)),
                    documentUrl = null,
                    searchTarget = "水 water"
                )
            )
        )
        srsSystemDao.upsertAll(listOf(defaultSrsSystem()))
        tokenRepository.saveToken("some-token")

        logoutCoordinator.logout()

        friendRepository.friendsFlow.test {
            val friends = awaitItem()
            assertThat(friends).hasSize(1)
            assertThat(friends.first().nickname).isEqualTo("rival")
        }
        settingsRepository.settings.test { assertThat(awaitItem().dailyLessonGoal).isEqualTo(15) }
        subjectDao.observeTotalCount().test { assertThat(awaitItem()).isEqualTo(1) }
        srsSystemDao.observeAll().test { assertThat(awaitItem()).hasSize(1) }
    }

    private fun defaultSrsSystem() = SrsSystemEntity(
        id = 0,
        name = "Default",
        unlockingStagePosition = 0,
        startingStagePosition = 1,
        passingStagePosition = 5,
        burningStagePosition = 9,
        stages = listOf(SrsStageData(position = 1, interval = 4, intervalUnit = "hours"))
    )
}
