package com.crazyfluff.shellfstudy.core.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.shared.data.OutboxRepository
import com.crazyfluff.shellfstudy.shared.database.AssignmentEntity
import com.crazyfluff.shellfstudy.shared.database.SubjectEntity
import com.crazyfluff.shellfstudy.shared.database.outbox.OutboxStatus
import com.crazyfluff.shellfstudy.shared.database.outbox.PendingReviewSubmissionEntity
import com.crazyfluff.shellfstudy.shared.network.MeaningData
import com.crazyfluff.shellfstudy.shared.network.ReadingData
import com.crazyfluff.shellfstudy.fakes.TestRepositories
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
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
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class OutboxSyncWorkerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

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

    private fun buildWorker(): OutboxSyncWorker =
        TestListenableWorkerBuilder<OutboxSyncWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = OutboxSyncWorker(
                    appContext, workerParameters,
                    repositories.outboxDao, repositories.waniKaniRepository, repositories.assignmentRepository, outboxRepository
                )
            })
            .build()

    /** Seeds both the assignment and its subject — optimistic/reconciliation logic needs the
     *  subject cached to resolve the (default, id=0) SRS system [buildTestRepositories] seeds. */
    private suspend fun seedAssignment(id: Long, subjectId: Long, srsStage: Int) {
        repositories.subjectDao.upsertAll(
            listOf(
                SubjectEntity(
                    id = subjectId, subjectType = "radical", level = 1, slug = "s$subjectId", characters = "字",
                    meanings = listOf(MeaningData(meaning = "Word", primary = true)),
                    readings = listOf(ReadingData(reading = "じ", primary = true)),
                    documentUrl = null, searchTarget = "s$subjectId"
                )
            )
        )
        repositories.assignmentDao.upsertAll(
            listOf(
                AssignmentEntity(
                    id = id, subjectId = subjectId, subjectType = "radical", srsStage = srsStage,
                    createdAt = "2026-01-01T00:00:00.000000Z", availableAt = "2020-01-01T00:00:00.000000Z", hidden = false
                )
            )
        )
    }

    private suspend fun queueReview(assignmentId: Long, subjectId: Long): Long =
        repositories.outboxDao.insertReviewSubmission(
            PendingReviewSubmissionEntity(
                assignmentId = assignmentId, subjectId = subjectId,
                incorrectMeaningAnswers = 0, incorrectReadingAnswers = 0, gradedAt = "2026-01-01T00:00:00.000000Z"
            )
        )

    @Test
    fun `doWork drains a pending review submission on success, reconciling the assignment and deleting the row`() = runTest {
        seedAssignment(id = 101, subjectId = 1, srsStage = 1)
        queueReview(assignmentId = 101, subjectId = 1)
        server.enqueue(jsonResponse(reviewResultJson(assignmentId = 101, subjectId = 1, startingStage = 1, endingStage = 2)))

        val result = buildWorker().doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        assertThat(repositories.outboxDao.allReviewSubmissions()).isEmpty()
        assertThat(repositories.assignmentDao.getById(101)?.srsStage).isEqualTo(2)
    }

    @Test
    fun `doWork retries on a transient server error, leaving the row pending`() = runTest {
        seedAssignment(id = 101, subjectId = 1, srsStage = 1)
        queueReview(assignmentId = 101, subjectId = 1)
        server.enqueue(emptyResponse(500))

        val result = buildWorker().doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.retry())
        val rows = repositories.outboxDao.allReviewSubmissions()
        assertThat(rows).hasSize(1)
        assertThat(rows.first().status).isEqualTo(OutboxStatus.PENDING.name)
    }

    @Test
    fun `doWork marks a terminal rejection and keeps draining the rest of the queue`() = runTest {
        seedAssignment(id = 101, subjectId = 1, srsStage = 1)
        seedAssignment(id = 102, subjectId = 2, srsStage = 1)
        queueReview(assignmentId = 101, subjectId = 1)
        queueReview(assignmentId = 102, subjectId = 2)
        server.enqueue(emptyResponse(422)) // row 101's submitReview — definitively rejected
        server.enqueue(jsonResponse(singleAssignmentJson(id = 101, subjectId = 1, srsStage = 1))) // refetchAssignment(101)
        server.enqueue(jsonResponse(reviewResultJson(assignmentId = 102, subjectId = 2, startingStage = 1, endingStage = 2))) // row 102 succeeds

        val result = buildWorker().doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        val rows = repositories.outboxDao.allReviewSubmissions()
        assertThat(rows).hasSize(1)
        assertThat(rows.first().assignmentId).isEqualTo(101L)
        assertThat(rows.first().status).isEqualTo(OutboxStatus.FAILED_TERMINAL.name)
        // Row 102 succeeded and was deleted, proving the loop didn't abort after the 422.
        assertThat(repositories.assignmentDao.getById(102)?.srsStage).isEqualTo(2)
    }

    @Test
    fun `doWork sets blockedOnAuth and stops on a 401, leaving pending rows untouched`() = runTest {
        seedAssignment(id = 101, subjectId = 1, srsStage = 1)
        queueReview(assignmentId = 101, subjectId = 1)
        server.enqueue(emptyResponse(401))

        val result = buildWorker().doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.failure())
        val rows = repositories.outboxDao.allReviewSubmissions()
        assertThat(rows).hasSize(1)
        assertThat(rows.first().status).isEqualTo(OutboxStatus.PENDING.name)
        outboxRepository.blockedOnAuth.test {
            assertThat(awaitItem()).isTrue()
        }
    }

    @Test
    fun `doWork clears a stale blockedOnAuth flag once a full pass succeeds`() = runTest {
        outboxRepository.setBlockedOnAuth(true)
        seedAssignment(id = 101, subjectId = 1, srsStage = 1)
        queueReview(assignmentId = 101, subjectId = 1)
        server.enqueue(jsonResponse(reviewResultJson(assignmentId = 101, subjectId = 1, startingStage = 1, endingStage = 2)))

        val result = buildWorker().doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        outboxRepository.blockedOnAuth.test {
            assertThat(awaitItem()).isFalse()
        }
    }

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
}
