package com.crazyfluff.shellfstudy.core.sync

import com.crazyfluff.shellfstudy.shared.data.ApiResult
import com.crazyfluff.shellfstudy.shared.database.SyncStateEntity
import com.crazyfluff.shellfstudy.fakes.TestRepositories
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.crazyfluff.shellfstudy.fakes.emptyResponse
import com.crazyfluff.shellfstudy.fakes.jsonResponse
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * syncAll() fans the four independent resources out with `async` after the two sequential ones —
 * so unlike the other repository tests, requests here can arrive at the server out of enqueue
 * order. A path-routing [Dispatcher] (rather than MockWebServer's default FIFO queue) is what lets
 * each endpoint get the response meant for it regardless of arrival order.
 */
class SyncOrchestratorTest {

    private lateinit var server: MockWebServer
    private lateinit var repositories: TestRepositories
    private val requestedPaths = mutableListOf<String>()
    private var reviewStatisticsShouldFail = false

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                requestedPaths += path
                return when {
                    path.startsWith("/review_statistics") && reviewStatisticsShouldFail -> emptyResponse(500)
                    path.startsWith("/spaced_repetition_systems") -> emptyCollection("srs_system")
                    path.startsWith("/subjects") -> emptyCollection("kanji")
                    path.startsWith("/assignments") -> emptyCollection("assignment")
                    path.startsWith("/review_statistics") -> emptyCollection("review_statistic")
                    path.startsWith("/level_progressions") -> emptyCollection("level_progression")
                    else -> emptyResponse(404)
                }
            }
        }
        server.start()
        repositories = buildTestRepositories(server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `syncAll returns Success once every resource syncs successfully`() = runTest {
        val result = repositories.syncOrchestrator.syncAll(force = true)

        assertThat(result).isEqualTo(ApiResult.Success(Unit))
        assertThat(pathsRequested()).containsAtLeast(
            "/spaced_repetition_systems", "/subjects", "/assignments",
            "/review_statistics", "/level_progressions"
        )
    }

    @Test
    fun `syncAll returns Error when one resource fails, but the others still sync`() = runTest {
        reviewStatisticsShouldFail = true

        val result = repositories.syncOrchestrator.syncAll(force = true)

        assertThat(result).isInstanceOf(ApiResult.Error::class.java)
        assertThat((result as ApiResult.Error).message).contains("WaniKani API error (500)")
        // The other two parallel syncs (assignments, level progressions) still ran to completion
        // despite review_statistics failing.
        assertThat(pathsRequested()).containsAtLeast(
            "/spaced_repetition_systems", "/subjects", "/assignments",
            "/review_statistics", "/level_progressions"
        )
    }

    @Test
    fun `fullRefresh clears every resource's sync cursor before resyncing`() = runTest {
        // Seed cursors as if a normal sync had already run — force(=true) alone would reuse these.
        listOf("subjects", "srs_systems", "assignments", "review_statistics").forEach { resource ->
            repositories.syncStateDao.upsert(
                SyncStateEntity(resource = resource, lastSyncedAt = "2020-01-01T00:00:00Z", lastSyncSuccessAt = "2020-01-01T00:00:00Z")
            )
        }

        val result = repositories.syncOrchestrator.fullRefresh()

        assertThat(result).isEqualTo(ApiResult.Success(Unit))
        // A non-null cursor would show up as `?updated_after=...` on every cursor-bearing request —
        // its absence proves fullRefresh() actually cleared the cursors rather than just bypassing
        // the staleness check the way syncAll(force = true) does.
        assertThat(requestedPaths.none { it.contains("updated_after") }).isTrue()
    }

    @Test
    fun `a concurrent fullRefresh waits for an in-flight syncAll instead of racing its cursor clear`() = runTest {
        repositories.syncStateDao.upsert(
            SyncStateEntity(resource = "subjects", lastSyncedAt = "2020-01-01T00:00:00Z", lastSyncSuccessAt = "2020-01-01T00:00:00Z")
        )
        val firstRequestReceived = CountDownLatch(1)
        val releaseFirstRequest = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                requestedPaths += path
                if (path.startsWith("/spaced_repetition_systems")) {
                    firstRequestReceived.countDown()
                    releaseFirstRequest.await(2, TimeUnit.SECONDS)
                }
                return when {
                    path.startsWith("/spaced_repetition_systems") -> emptyCollection("srs_system")
                    path.startsWith("/subjects") -> emptyCollection("kanji")
                    path.startsWith("/assignments") -> emptyCollection("assignment")
                    path.startsWith("/review_statistics") -> emptyCollection("review_statistic")
                    path.startsWith("/level_progressions") -> emptyCollection("level_progression")
                    else -> emptyResponse(404)
                }
            }
        }

        // Runs on a real dispatcher (not the test scheduler) so it genuinely executes concurrently
        // with the assertions below instead of being virtual-time-scheduled around them.
        val syncAllJob = async(Dispatchers.Default) { repositories.syncOrchestrator.syncAll(force = true) }
        assertThat(firstRequestReceived.await(2, TimeUnit.SECONDS)).isTrue()
        // syncAll is now parked mid-request, holding the sync mutex.

        val fullRefreshJob = async(Dispatchers.Default) { repositories.syncOrchestrator.fullRefresh() }
        // No signal to wait on here by design: proving fullRefresh hasn't started yet is exactly
        // proving a negative, so a bounded real-time pause is unavoidable — mirrors the accepted
        // real-wait precedent in MainDispatcherRule.settleRealThreadHandoffs.
        Thread.sleep(200)
        assertThat(repositories.syncStateDao.get("subjects")).isNotNull() // fullRefresh hasn't cleared cursors yet

        releaseFirstRequest.countDown()
        assertThat(syncAllJob.await()).isEqualTo(ApiResult.Success(Unit))
        assertThat(fullRefreshJob.await()).isEqualTo(ApiResult.Success(Unit))
        // fullRefresh's clear-then-resync only ran (and only completed) after syncAll released the
        // mutex, so the seeded 2020 cursor is gone, replaced by a freshly-written one.
        assertThat(repositories.syncStateDao.get("subjects")?.lastSyncedAt).isNotEqualTo("2020-01-01T00:00:00Z")
    }

    private fun pathsRequested(): List<String> = requestedPaths.map { it.substringBefore('?') }

    private fun emptyCollection(objectType: String) = jsonResponse(
        """{"object":"$objectType","url":"https://api.wanikani.com/v2/$objectType","data":[]}"""
    )
}
