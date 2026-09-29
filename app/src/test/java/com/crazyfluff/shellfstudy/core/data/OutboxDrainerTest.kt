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
    fun `drain posts the row's real wrong-answer counts, not a flattened 0 or 1`() = runTest {
        // WaniKani computes the ending stage from ceil(incorrect / 2) * penalty, so the counts on the
        // row must reach the API intact — collapsing them back to booleans here would silently
        // reintroduce the under-reporting this was fixed for.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    request.method == "POST" && path.startsWith("/reviews") -> jsonResponse(reviewResultJson(101, 1, 5, 3))
                    else -> emptyResponse(404)
                }
            }
        }
        repositories.outboxDao.insertReviewSubmission(
            PendingReviewSubmissionEntity(
                assignmentId = 101, subjectId = 1,
                incorrectMeaningAnswers = 2, incorrectReadingAnswers = 1,
                gradedAt = "2026-01-01T00:00:00.000000Z"
            )
        )

        val outcome = buildDrainer().drain()

        assertThat(outcome).isEqualTo(DrainOutcome.SUCCESS)
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"incorrect_meaning_answers\":2")
        assertThat(body).contains("\"incorrect_reading_answers\":1")
        assertThat(repositories.outboxDao.allReviewSubmissions()).isEmpty()
    }

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

    /**
     * The API rate-limits at 60 requests a minute, so a long session's drain can be throttled through
     * no fault of its own. 429 is a 4xx, and the drain used to treat every 4xx except 401 as terminal
     * — which retired the row as `FAILED_TERMINAL`, and since the drain only reads `PENDING` rows it
     * was never tried again. The graded review was lost for good while the local assignment kept the
     * optimistic stage it had been given.
     */
    @Test
    fun `a rate-limited review stays pending rather than being retired as terminal`() = runTest {
        queueReview(assignmentId = 102, subjectId = 2)
        server.enqueue(emptyResponse(429))

        val outcome = buildDrainer().drain()

        assertThat(outcome).isEqualTo(DrainOutcome.RETRY)
        assertThat(repositories.outboxDao.allReviewSubmissions().first().status).isEqualTo(OutboxStatus.PENDING.name)
    }

    /** Same reasoning as 429: a request timeout is transient, not a rejection. */
    @Test
    fun `a timed-out review stays pending rather than being retired as terminal`() = runTest {
        queueReview(assignmentId = 102, subjectId = 2)
        server.enqueue(emptyResponse(408))

        val outcome = buildDrainer().drain()

        assertThat(outcome).isEqualTo(DrainOutcome.RETRY)
        assertThat(repositories.outboxDao.allReviewSubmissions().first().status).isEqualTo(OutboxStatus.PENDING.name)
    }

    /**
     * Rows are independent submissions, so a server-side failure on one must not hold up the rest.
     * Before this, the drain returned RETRY at the first transient failure, which meant a row that
     * consistently 5xx'd blocked every submission queued behind it — for as long as it kept failing,
     * which for a malformed payload is forever.
     */
    @Test
    fun `a review the server rejects with a 500 does not block the reviews queued behind it`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                val body = request.body.readUtf8()
                return when {
                    request.method == "POST" && path.startsWith("/reviews") && body.contains("102") ->
                        emptyResponse(500)
                    request.method == "POST" && path.startsWith("/reviews") && body.contains("101") ->
                        jsonResponse(reviewResultJson(101, 1, 1, 2))
                    request.method == "POST" && path.startsWith("/reviews") && body.contains("103") ->
                        jsonResponse(reviewResultJson(103, 3, 1, 2))
                    else -> emptyResponse(404)
                }
            }
        }
        queueReview(assignmentId = 101, subjectId = 1)
        queueReview(assignmentId = 102, subjectId = 2)
        queueReview(assignmentId = 103, subjectId = 3)

        val outcome = buildDrainer().drain()

        // The pass still reports that something needs retrying…
        assertThat(outcome).isEqualTo(DrainOutcome.RETRY)
        // …but only the rejected row is left for it.
        assertThat(repositories.outboxDao.allReviewSubmissions().map { it.assignmentId })
            .containsExactly(102L)
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
