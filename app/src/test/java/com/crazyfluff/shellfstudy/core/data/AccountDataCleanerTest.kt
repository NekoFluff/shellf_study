package com.crazyfluff.shellfstudy.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.fakes.FakeAssignmentDao
import com.crazyfluff.shellfstudy.fakes.FakeLevelProgressionDao
import com.crazyfluff.shellfstudy.fakes.FakeOutboxDao
import com.crazyfluff.shellfstudy.fakes.FakeOutboxSyncScheduler
import com.crazyfluff.shellfstudy.fakes.FakeReviewStatisticDao
import com.crazyfluff.shellfstudy.fakes.FakeStudyActivityDao
import com.crazyfluff.shellfstudy.fakes.FakeSyncStateDao
import com.crazyfluff.shellfstudy.shared.data.AccountDataCleaner
import com.crazyfluff.shellfstudy.shared.data.DashboardCacheRepository
import com.crazyfluff.shellfstudy.shared.data.LastSessionKind
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummary
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummaryRepository
import com.crazyfluff.shellfstudy.shared.data.LessonSessionRepository
import com.crazyfluff.shellfstudy.shared.data.OutboxRepository
import com.crazyfluff.shellfstudy.shared.data.PersistedLessonSession
import com.crazyfluff.shellfstudy.shared.data.PersistedQuestion
import com.crazyfluff.shellfstudy.shared.data.PersistedReviewSession
import com.crazyfluff.shellfstudy.shared.data.ReviewSessionRepository
import com.crazyfluff.shellfstudy.shared.database.AssignmentDao
import com.crazyfluff.shellfstudy.shared.database.AssignmentEntity
import com.crazyfluff.shellfstudy.shared.database.LevelProgressionEntity
import com.crazyfluff.shellfstudy.shared.database.ReviewStatisticEntity
import com.crazyfluff.shellfstudy.shared.database.SyncStateEntity
import com.crazyfluff.shellfstudy.shared.database.outbox.PendingLessonStartEntity
import com.crazyfluff.shellfstudy.shared.database.outbox.PendingReviewSubmissionEntity
import com.crazyfluff.shellfstudy.shared.database.studyactivity.StudyActivityDayEntity
import com.crazyfluff.shellfstudy.shared.session.LessonSessionController
import com.crazyfluff.shellfstudy.shared.session.ReviewSessionController
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Throws from [clearAll] but forwards everything else — lets the resilience test simulate one
 *  store failing to clear without needing [FakeAssignmentDao] itself to be open for subclassing. */
private class ThrowingAssignmentDao(private val delegate: AssignmentDao) : AssignmentDao by delegate {
    override suspend fun clearAll() {
        throw IllegalStateException("boom")
    }
}

class AccountDataCleanerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var json: Json
    private lateinit var assignmentDao: FakeAssignmentDao
    private lateinit var reviewStatisticDao: FakeReviewStatisticDao
    private lateinit var levelProgressionDao: FakeLevelProgressionDao
    private lateinit var syncStateDao: FakeSyncStateDao
    private lateinit var outboxDao: FakeOutboxDao
    private lateinit var studyActivityDao: FakeStudyActivityDao
    private lateinit var outboxRepository: OutboxRepository
    private lateinit var dashboardCacheRepository: DashboardCacheRepository
    private lateinit var lastSessionSummaryRepository: LastSessionSummaryRepository
    private lateinit var reviewSessionRepository: ReviewSessionRepository
    private lateinit var lessonSessionRepository: LessonSessionRepository
    private lateinit var reviewSessionController: ReviewSessionController
    private lateinit var lessonSessionController: LessonSessionController

    private fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        json = Json { ignoreUnknownKeys = true }
        assignmentDao = FakeAssignmentDao()
        reviewStatisticDao = FakeReviewStatisticDao()
        levelProgressionDao = FakeLevelProgressionDao()
        syncStateDao = FakeSyncStateDao()
        outboxDao = FakeOutboxDao()
        studyActivityDao = FakeStudyActivityDao()
        outboxRepository = OutboxRepository(outboxDao, FakeOutboxSyncScheduler(), dataStore)
        dashboardCacheRepository = DashboardCacheRepository(dataStore)
        lastSessionSummaryRepository = LastSessionSummaryRepository(dataStore, json)
        reviewSessionRepository = ReviewSessionRepository(dataStore, json)
        lessonSessionRepository = LessonSessionRepository(dataStore, json)
        reviewSessionController = ReviewSessionController(CoroutineScope(SupervisorJob()), reviewSessionRepository)
        lessonSessionController = LessonSessionController(CoroutineScope(SupervisorJob()), lessonSessionRepository)
    }

    private fun buildCleaner(assignmentDao: AssignmentDao = this.assignmentDao) = AccountDataCleaner(
        assignmentDao = assignmentDao,
        reviewStatisticDao = reviewStatisticDao,
        levelProgressionDao = levelProgressionDao,
        syncStateDao = syncStateDao,
        outboxDao = outboxDao,
        studyActivityDao = studyActivityDao,
        outboxRepository = outboxRepository,
        dashboardCacheRepository = dashboardCacheRepository,
        lastSessionSummaryRepository = lastSessionSummaryRepository,
        reviewSessionController = reviewSessionController,
        lessonSessionController = lessonSessionController
    )

    private suspend fun seedEverything() {
        assignmentDao.upsertAll(
            listOf(AssignmentEntity(id = 1, subjectId = 1, subjectType = "kanji", srsStage = 1, createdAt = "2026-08-01T00:00:00Z", hidden = false))
        )
        reviewStatisticDao.upsertAll(
            listOf(
                ReviewStatisticEntity(
                    id = 1, subjectId = 1, subjectType = "kanji",
                    meaningCorrect = 1, meaningIncorrect = 0, meaningMaxStreak = 1, meaningCurrentStreak = 1,
                    readingCorrect = 1, readingIncorrect = 0, readingMaxStreak = 1, readingCurrentStreak = 1,
                    percentageCorrect = 100, hidden = false
                )
            )
        )
        levelProgressionDao.upsertAll(
            listOf(
                LevelProgressionEntity(
                    id = 1, level = 1, createdAt = "2026-08-01T00:00:00Z",
                    unlockedAt = null, startedAt = null, passedAt = null, completedAt = null, abandonedAt = null
                )
            )
        )
        syncStateDao.upsert(SyncStateEntity(resource = "assignments", lastSyncedAt = "2026-08-01T00:00:00Z"))
        outboxDao.insertReviewSubmission(
            PendingReviewSubmissionEntity(assignmentId = 1, subjectId = 1, incorrectMeaningAnswers = 0, incorrectReadingAnswers = 0, gradedAt = "2026-08-01T00:00:00Z")
        )
        outboxDao.insertLessonStart(
            PendingLessonStartEntity(assignmentId = 1, subjectId = 1, startedAt = "2026-08-01T00:00:00Z")
        )
        studyActivityDao.markActive(StudyActivityDayEntity(date = "2026-08-01"))
        outboxRepository.setBlockedOnAuth(true)
        dashboardCacheRepository.save(username = "durtle_fan", level = 5, lessonCount = 3, reviewCount = 7, syncedAtMillis = 1L)
        lastSessionSummaryRepository.save(
            LastSessionSummary(
                kind = LastSessionKind.REVIEW, itemsCount = 1, correctFirstTry = 1, totalElapsedMs = 1000L,
                averageTimePerItemMs = 1000L, slowestAnswers = emptyList(), missedItems = emptyList(), completedAtMillis = 1L
            )
        )
        reviewSessionRepository.save(
            PersistedReviewSession(queue = listOf(PersistedQuestion(assignmentId = 1, questionType = "MEANING")), progress = emptyList(), totalQuestions = 1)
        )
        lessonSessionRepository.save(PersistedLessonSession(studyAssignmentIds = listOf(1)))
    }

    @Test
    fun `clearAll wipes every account-scoped store`() = runTest {
        setUp()
        seedEverything()

        buildCleaner().clearAll()

        assertThat(assignmentDao.getById(1)).isNull()
        reviewStatisticDao.observeAll().test { assertThat(awaitItem()).isEmpty() }
        levelProgressionDao.observeAll().test { assertThat(awaitItem()).isEmpty() }
        assertThat(syncStateDao.get("assignments")).isNull()
        assertThat(outboxDao.allReviewSubmissions()).isEmpty()
        assertThat(outboxDao.allLessonStarts()).isEmpty()
        studyActivityDao.observeActiveDays().test { assertThat(awaitItem()).isEmpty() }
        outboxRepository.blockedOnAuth.test { assertThat(awaitItem()).isFalse() }
        dashboardCacheRepository.cachedSummary.test { assertThat(awaitItem()).isNull() }
        assertThat(lastSessionSummaryRepository.loadReview()).isNull()
        assertThat(reviewSessionRepository.load()).isNull()
        assertThat(lessonSessionRepository.load()).isNull()
    }

    @Test
    fun `one store failing to clear does not block the rest`() = runTest {
        setUp()
        seedEverything()

        val throwing = ThrowingAssignmentDao(assignmentDao)
        buildCleaner(assignmentDao = throwing).clearAll()

        // The throwing dao's own data survives (its clearAll never completed)...
        assertThat(assignmentDao.getById(1)).isNotNull()
        // ...but every other independent store still got cleared.
        assertThat(syncStateDao.get("assignments")).isNull()
        assertThat(outboxDao.allReviewSubmissions()).isEmpty()
        assertThat(outboxDao.allLessonStarts()).isEmpty()
        studyActivityDao.observeActiveDays().test { assertThat(awaitItem()).isEmpty() }
        assertThat(lastSessionSummaryRepository.loadReview()).isNull()
        assertThat(reviewSessionRepository.load()).isNull()
        assertThat(lessonSessionRepository.load()).isNull()
    }
}
