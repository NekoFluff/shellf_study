package com.crazyfluff.shellfstudy.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.shared.data.DrainOutcome
import com.crazyfluff.shellfstudy.shared.data.OutboxDrainer
import com.crazyfluff.shellfstudy.shared.data.OutboxRepository
import com.crazyfluff.shellfstudy.shared.database.outbox.OutboxStatus
import com.crazyfluff.shellfstudy.shared.database.outbox.PendingLessonStartEntity
import com.crazyfluff.shellfstudy.shared.database.outbox.PendingReviewSubmissionEntity
import com.crazyfluff.shellfstudy.fakes.TestRepositories
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.crazyfluff.shellfstudy.fakes.emptyResponse
import com.crazyfluff.shellfstudy.fakes.jsonResponse
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Exercises [OutboxDrainer] directly — without the WorkManager machinery — so lesson-start drain
 * paths and ordering guarantees are tested independently of review-submission drain paths.
 * (OutboxSyncWorkerTest covers the same drain logic end-to-end via the worker, but uses only
 * review submissions; this fills the lesson-start gap.)
 */
class OutboxDrainerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var repositories: TestRepositories
    private lateinit var outboxRepository: OutboxRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repositories = buildTestRepositories(server.url("/").toString())
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        outboxRepository = OutboxRepository(repositories.outboxDao, repositories.outboxSyncScheduler, dataStore)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun buildDrainer() = OutboxDrainer(
        outboxDao = repositories.outboxDao,
        waniKaniRepository = repositories.waniKaniRepository,
        assignmentRepository = repositories.assignmentRepository,
        outboxRepository = outboxRepository
    )

    private suspend fun queueLesson(assignmentId: Long, subjectId: Long) =
        repositories.outboxDao.insertLessonStart(
            PendingLessonStartEntity(assignmentId = assignmentId, subjectId = subjectId, startedAt = "2026-01-01T00:00:00.000000Z")
        )

    private suspend fun queueReview(assignmentId: Long, subjectId: Long) =
        repositories.outboxDao.insertReviewSubmission(
            PendingReviewSubmissionEntity(
                assignmentId = assignmentId, subjectId = subjectId,
                incorrectMeaningAnswers = 0, incorrectReadingAnswers = 0, gradedAt = "2026-01-01T00:00:00.000000Z"
            )
        )

    @Test
    fun `drains a pending lesson start successfully and deletes the row`() = runTest {
        queueLesson(assignmentId = 101, subjectId = 1)
        server.enqueue(jsonResponse(startedAssignmentJson(id = 101, subjectId = 1)))

        val outcome = buildDrainer().drain()

        assertThat(outcome).isEqualTo(DrainOutcome.SUCCESS)
        assertThat(repositories.outboxDao.allLessonStarts()).isEmpty()
    }

    @Test
    fun `drains lessons first then reviews — a single pass clears both queues`() = runTest {
        // Lesson must POST before review, since the assignment has to exist server-side first.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    request.method == "PUT" && path.contains("/start") -> jsonResponse(startedAssignmentJson(101, 1))
                    request.method == "POST" && path.startsWith("/reviews") -> jsonResponse(reviewResultJson(101, 1, 1, 2))
                    else -> emptyResponse(404)
                }
            }
        }
        queueLesson(assignmentId = 101, subjectId = 1)
        queueReview(assignmentId = 101, subjectId = 1)

        val outcome = buildDrainer().drain()

        assertThat(outcome).isEqualTo(DrainOutcome.SUCCESS)
        assertThat(repositories.outboxDao.allLessonStarts()).isEmpty()
        assertThat(repositories.outboxDao.allReviewSubmissions()).isEmpty()
    }

    @Test
    fun `lesson auth failure stops the drain before reviews are attempted`() = runTest {
        queueLesson(assignmentId = 101, subjectId = 1)
        queueReview(assignmentId = 102, subjectId = 2)
        server.enqueue(emptyResponse(401))

        val outcome = buildDrainer().drain()

        assertThat(outcome).isEqualTo(DrainOutcome.AUTH_FAILURE)
        // Lesson row stays pending, review row untouched.
        assertThat(repositories.outboxDao.allLessonStarts().first().status).isEqualTo(OutboxStatus.PENDING.name)
        assertThat(repositories.outboxDao.allReviewSubmissions().first().status).isEqualTo(OutboxStatus.PENDING.name)
        outboxRepository.blockedOnAuth.test {
            assertThat(awaitItem()).isTrue()
        }
    }

    @Test
    fun `a terminal lesson rejection is marked and the drain continues to reviews`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    request.method == "PUT" && path.contains("/start") -> emptyResponse(422)
                    request.method == "GET" && path.startsWith("/assignments") -> jsonResponse(singleAssignmentJson(101, 1, 1))
                    request.method == "POST" && path.startsWith("/reviews") -> jsonResponse(reviewResultJson(102, 2, 1, 2))
                    else -> emptyResponse(404)
                }
            }
        }
        queueLesson(assignmentId = 101, subjectId = 1)
        queueReview(assignmentId = 102, subjectId = 2)

        val outcome = buildDrainer().drain()

        assertThat(outcome).isEqualTo(DrainOutcome.SUCCESS)
        assertThat(repositories.outboxDao.allLessonStarts().first().status).isEqualTo(OutboxStatus.FAILED_TERMINAL.name)
        assertThat(repositories.outboxDao.allReviewSubmissions()).isEmpty()
    }

    @Test
    fun `retry on a transient lesson error leaves the row pending and skips reviews`() = runTest {
        queueLesson(assignmentId = 101, subjectId = 1)
        queueReview(assignmentId = 102, subjectId = 2)
        server.enqueue(emptyResponse(500))

        val outcome = buildDrainer().drain()

        assertThat(outcome).isEqualTo(DrainOutcome.RETRY)
        assertThat(repositories.outboxDao.allLessonStarts().first().status).isEqualTo(OutboxStatus.PENDING.name)
        assertThat(repositories.outboxDao.allReviewSubmissions().first().status).isEqualTo(OutboxStatus.PENDING.name)
    }

    private fun startedAssignmentJson(id: Long, subjectId: Long) = """
        {
          "id": $id, "object": "assignment", "url": "https://api.wanikani.com/v2/assignments/$id",
          "data_updated_at": "2026-01-01T00:00:00.000000Z",
          "data": {
            "created_at": "2026-01-01T00:00:00.000000Z", "subject_id": $subjectId, "subject_type": "radical",
            "srs_stage": 1, "started_at": "2026-01-01T00:00:00.000000Z", "hidden": false
          }
        }
    """.trimIndent()

    private fun singleAssignmentJson(id: Long, subjectId: Long, srsStage: Int) = """
        {
          "id": $id, "object": "assignment", "url": "https://api.wanikani.com/v2/assignments/$id",
          "data_updated_at": "2026-01-01T00:00:00.000000Z",
          "data": {
            "created_at": "2026-01-01T00:00:00.000000Z", "subject_id": $subjectId, "subject_type": "radical",
            "srs_stage": $srsStage, "hidden": false
          }
        }
    """.trimIndent()

    private fun reviewResultJson(assignmentId: Long, subjectId: Long, startingStage: Int, endingStage: Int) = """
        {
          "id": 1, "object": "review", "url": "https://api.wanikani.com/v2/reviews/1",
          "data_updated_at": "2026-01-01T00:00:00.000000Z",
          "data": {
            "assignment_id": $assignmentId, "subject_id": $subjectId,
            "starting_srs_stage": $startingStage, "ending_srs_stage": $endingStage,
            "incorrect_meaning_answers": 0, "incorrect_reading_answers": 0,
            "created_at": "2026-01-01T00:00:00.000000Z"
          }
        }
    """.trimIndent()
}
