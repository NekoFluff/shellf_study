package com.crazyfluff.shellfstudy.core.sync

import com.crazyfluff.shellfstudy.shared.data.ApiResult
import com.crazyfluff.shellfstudy.shared.database.SyncStateEntity
import com.crazyfluff.shellfstudy.fakes.waniKaniCollectionDispatcher
import com.crazyfluff.shellfstudy.fakes.TestRepositories
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.crazyfluff.shellfstudy.fakes.emptyResponse
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockWebServer
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
    /**
     * Recorded from MockWebServer's dispatcher thread(s) and read from the test thread, so it must be
     * synchronised. It was a plain `mutableListOf`, and under a full-suite run the test that asserts a
     * failing resource was still requested intermittently lost that entry — the write and the read had
     * no happens-before relationship, and dispatcher threads are not the test's coroutine.
     *
     * Passes in isolation and fails occasionally in a full suite, which is the signature.
     */
    private val requestedPaths = java.util.Collections.synchronizedList(mutableListOf<String>())
    private var reviewStatisticsShouldFail = false

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = waniKaniCollectionDispatcher { request ->
            val path = request.target.orEmpty()
            requestedPaths += path
            if (reviewStatisticsShouldFail && path.startsWith("/review_statistics")) emptyResponse(500) else null
        }
        server.start()
        repositories = buildTestRepositories(server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.close()
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
    fun `syncQueue fetches only what a quiz queue joins against, and nothing once it is fresh`() = runTest {
        val first = repositories.syncOrchestrator.syncQueue()

        assertThat(first).isEqualTo(ApiResult.Success(Unit))
        assertThat(pathsRequested()).containsExactly("/spaced_repetition_systems", "/subjects", "/assignments")

        // Straight after, every one of those is inside its freshness window.
        requestedPaths.clear()
        val second = repositories.syncOrchestrator.syncQueue()

        assertThat(second).isEqualTo(ApiResult.Success(Unit))
        assertThat(requestedPaths).isEmpty()
    }

    @Test
    fun `syncQueue does not fail over a statistics endpoint it never needed`() = runTest {
        reviewStatisticsShouldFail = true

        assertThat(repositories.syncOrchestrator.syncQueue()).isEqualTo(ApiResult.Success(Unit))
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
        assertThat(pathsRequested().none { it.contains("updated_after") }).isTrue()
    }

    @Test
    fun `a concurrent fullRefresh waits for an in-flight syncAll instead of racing its cursor clear`() = runTest {
        repositories.syncStateDao.upsert(
            SyncStateEntity(resource = "subjects", lastSyncedAt = "2020-01-01T00:00:00Z", lastSyncSuccessAt = "2020-01-01T00:00:00Z")
        )
        val firstRequestReceived = CountDownLatch(1)
        val releaseFirstRequest = CountDownLatch(1)
        server.dispatcher = waniKaniCollectionDispatcher { request ->
            val path = request.target.orEmpty()
            requestedPaths += path
            if (path.startsWith("/spaced_repetition_systems")) {
                firstRequestReceived.countDown()
                releaseFirstRequest.await(2, TimeUnit.SECONDS)
            }
            null
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

    /**
     * The whole point of the fetch/persist split: five resources, one database transaction.
     *
     * Room broadcasts invalidation — re-running every observable query in the app — once per
     * *outermost* write operation, so a pass that wrote each resource separately woke the dashboard's
     * dozen-odd flows once per resource. Asserting the count is what stops a later refactor from
     * quietly going back to one write per resource, which no functional test would notice.
     */
    @Test
    fun `a sync pass writes every resource inside a single transaction`() = runTest {
        val result = repositories.syncOrchestrator.syncAll(force = true)

        assertThat(result).isEqualTo(ApiResult.Success(Unit))
        assertThat(repositories.syncTransactionRunner.transactionCount).isEqualTo(1)
    }

    /**
     * And it groups only the writes. A Room database has exactly one write connection, so issuing a
     * network fetch while holding it would block every other writer in the app for the fetch's
     * duration — and can throw once the connection is held past its timeout.
     *
     * Asserted as "no request arrives *between* entering and leaving the transaction", not as "every
     * request arrived before it". The weaker phrasing is a race: the fetches run concurrently, so
     * which of them has reached the server by the time the write connection is taken is not
     * deterministic, and a test written that way fails intermittently for no real reason.
     */
    @Test
    fun `no fetch happens while the transaction is open`() = runTest {
        var requestsAtEntry = -1
        var requestsAtExit = -1
        repositories.syncTransactionRunner.onEnter = { requestsAtEntry = server.requestCount }
        repositories.syncTransactionRunner.onExit = { requestsAtExit = server.requestCount }

        repositories.syncOrchestrator.syncAll(force = true)

        assertThat(requestsAtEntry).isAtLeast(0)
        assertThat(requestsAtExit).isEqualTo(requestsAtEntry)
    }

    /** A pass where every resource is already fresh must not open a transaction at all. */
    @Test
    fun `a pass with nothing to sync does not open a transaction`() = runTest {
        repositories.syncOrchestrator.syncAll(force = true) // leaves every cursor fresh
        val afterFirstPass = repositories.syncTransactionRunner.transactionCount

        repositories.syncOrchestrator.syncAll(force = false)

        assertThat(repositories.syncTransactionRunner.transactionCount).isEqualTo(afterFirstPass)
    }

    /** Snapshot under the list's own lock — iterating a synchronizedList outside one is still unsafe. */
    private fun pathsRequested(): List<String> = synchronized(requestedPaths) {
        requestedPaths.toList()
    }.map { it.substringBefore('?') }

}
