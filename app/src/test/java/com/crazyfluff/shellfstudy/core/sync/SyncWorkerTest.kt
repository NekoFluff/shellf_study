package com.crazyfluff.shellfstudy.core.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
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
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncWorkerTest {

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

    private fun buildWorker(): SyncWorker {
        val repos = buildTestRepositories(server.url("/").toString())
        return buildTestWorker(context) { appContext, params ->
            SyncWorker(
                appContext = appContext,
                params = params,
                syncOrchestrator = repos.syncOrchestrator,
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

}
