package com.crazyfluff.shellfstudy.core.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.work.ListenableWorker
import com.crazyfluff.shellfstudy.shared.data.OutboxDrainer
import com.crazyfluff.shellfstudy.shared.data.OutboxRepository
import com.crazyfluff.shellfstudy.shared.database.outbox.PendingReviewSubmissionEntity
import com.crazyfluff.shellfstudy.fakes.waniKaniCollectionDispatcher
import com.crazyfluff.shellfstudy.fakes.buildTestWorker
import com.crazyfluff.shellfstudy.fakes.emptyCollection
import com.crazyfluff.shellfstudy.fakes.FakeNotificationCoordinator
import com.crazyfluff.shellfstudy.fakes.FakeOutboxSyncScheduler
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncWorkerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var server: MockWebServer
    private lateinit var notificationCoordinator: FakeNotificationCoordinator
    private lateinit var outboxSyncScheduler: FakeOutboxSyncScheduler
    private var reviewStatisticsShouldFail = false

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = waniKaniCollectionDispatcher { request ->
            if (reviewStatisticsShouldFail && request.target.orEmpty().startsWith("/review_statistics")) {
                emptyCollection("review_statistic", 500)
            } else {
                null
            }
        }
        server.start()
        notificationCoordinator = FakeNotificationCoordinator()
        outboxSyncScheduler = FakeOutboxSyncScheduler()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private val repos by lazy { buildTestRepositories(server.url("/").toString()) }

    private fun buildWorker(): SyncWorker {
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { tempFolder.newFile("test.preferences_pb") })
        val drainer = OutboxDrainer(
            outboxDao = repos.outboxDao,
            waniKaniRepository = repos.waniKaniRepository,
            assignmentRepository = repos.assignmentRepository,
            outboxRepository = OutboxRepository(repos.outboxDao, repos.outboxSyncScheduler, dataStore)
        )
        return buildTestWorker(context) { appContext, params ->
            SyncWorker(
                appContext = appContext,
                params = params,
                syncOrchestrator = repos.syncOrchestrator,
                outboxDrainer = drainer,
                notificationCoordinator = notificationCoordinator,
                outboxSyncScheduler = outboxSyncScheduler
            )
        }
    }

    @Test
    fun `doWork returns success, fires notification hooks, and nudges the outbox on a clean sync`() = runTest {
        val result = buildWorker().doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        assertThat(notificationCoordinator.evaluateReviewsAndBacklogCallCount).isEqualTo(1)
        assertThat(notificationCoordinator.rescheduleNextReviewCheckCallCount).isEqualTo(1)
        assertThat(notificationCoordinator.rescheduleLevelUpReminderCallCount).isEqualTo(1)
        assertThat(outboxSyncScheduler.requestCount).isEqualTo(1)
    }

    @Test
    fun `doWork retries on a sync error and skips notification hooks`() = runTest {
        reviewStatisticsShouldFail = true

        val result = buildWorker().doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.retry())
        assertThat(notificationCoordinator.evaluateReviewsAndBacklogCallCount).isEqualTo(0)
        assertThat(outboxSyncScheduler.requestCount).isEqualTo(0)
    }

    @Test
    fun `doWork drains the outbox before it syncs`() = runTest {
        repos.outboxDao.insertReviewSubmission(
            PendingReviewSubmissionEntity(
                assignmentId = 101, subjectId = 1,
                incorrectMeaningAnswers = 0, incorrectReadingAnswers = 0, gradedAt = "2026-01-01T00:00:00Z"
            )
        )

        buildWorker().doWork()

        val firstRequest = server.takeRequest()
        assertThat(firstRequest.method).isEqualTo("POST")
        assertThat(firstRequest.target.orEmpty()).startsWith("/reviews")
    }
}
