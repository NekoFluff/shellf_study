package com.crazyfluff.shellfstudy.core.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.crazyfluff.shellfstudy.fakes.FakeNotificationCoordinator
import com.crazyfluff.shellfstudy.fakes.FakeOutboxSyncScheduler
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.crazyfluff.shellfstudy.fakes.jsonResponse
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SyncWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var server: MockWebServer
    private lateinit var notificationCoordinator: FakeNotificationCoordinator
    private lateinit var outboxSyncScheduler: FakeOutboxSyncScheduler
    private var reviewStatisticsShouldFail = false

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/review_statistics") && reviewStatisticsShouldFail -> emptyCollection("review_statistic", 500)
                    path.startsWith("/spaced_repetition_systems") -> emptyCollection("srs_system")
                    path.startsWith("/subjects") -> emptyCollection("kanji")
                    path.startsWith("/assignments") -> emptyCollection("assignment")
                    path.startsWith("/review_statistics") -> emptyCollection("review_statistic")
                    path.startsWith("/study_materials") -> emptyCollection("study_material")
                    path.startsWith("/level_progressions") -> emptyCollection("level_progression")
                    else -> jsonResponse("{}", 404)
                }
            }
        }
        server.start()
        notificationCoordinator = FakeNotificationCoordinator()
        outboxSyncScheduler = FakeOutboxSyncScheduler()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun buildWorker(): SyncWorker {
        val repos = buildTestRepositories(server.url("/").toString())
        return TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = SyncWorker(
                    appContext = appContext,
                    params = workerParameters,
                    syncOrchestrator = repos.syncOrchestrator,
                    notificationCoordinator = notificationCoordinator,
                    outboxSyncScheduler = outboxSyncScheduler
                )
            })
            .build()
    }

    @Test
    fun `doWork returns success, fires notification hooks, and nudges the outbox on a clean sync`() = runTest {
        val result = buildWorker().doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        assertThat(notificationCoordinator.evaluateReviewsAndBacklogCallCount).isEqualTo(1)
        assertThat(notificationCoordinator.rescheduleNextReviewCheckCallCount).isEqualTo(1)
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

    private fun emptyCollection(objectType: String, code: Int = 200): MockResponse =
        jsonResponse("""{"object":"$objectType","url":"https://api.wanikani.com/v2/$objectType","data":[]}""", code)
}
