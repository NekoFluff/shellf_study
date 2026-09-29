package com.crazyfluff.shellfstudy.core.sync

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
import org.junit.Test

/**
 * Covers what the dashboard's resume path costs, which is the frame-time question rather than a
 * behavioural one.
 *
 * The resume path used to force assignments unconditionally, bypassing the staleness gate. An
 * incremental fetch still returns whatever WaniKani has touched, and the write re-inserts every
 * returned row with `INSERT OR REPLACE` — on an account with a few thousand assignments that is
 * thousands of row rewrites maintaining five indexes each, on every single return to the dashboard.
 * Measured on-device, that was the session's worst frames: 272 ms and 244 ms, both on a
 * fully-loaded dashboard where nothing needed refreshing.
 *
 * These tests assert the write volume, because that is the cost. The end state is identical either
 * way — the rows are already correct — so no behavioural test can see the difference.
 */
class DashboardResumeSyncTest {

    private lateinit var server: MockWebServer
    private lateinit var repositories: TestRepositories

    /**
     * Every resource returns something: assignments return a real row, the rest are empty.
     *
     * The assignments response must be non-empty or these tests prove nothing. With an empty response
     * the no-op write guard skips the write regardless of whether the fetch was forced, so a
     * write-count assertion passes either way — which is exactly what happened when this test was
     * first written, and why it failed to catch the regression it exists for.
     */
    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = assignmentsReturnOneRow()
        server.start()
        repositories = buildTestRepositories(server.url("/").toString())
    }

    private fun assignmentsReturnOneRow() = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path.orEmpty()
            return when {
                path.startsWith("/spaced_repetition_systems") -> emptyCollection("srs_system")
                path.startsWith("/subjects") -> emptyCollection("kanji")
                path.startsWith("/assignments") -> jsonResponse(
                    """{"object":"collection","url":"https://api.wanikani.com/v2/assignments","total_count":1,"data":[
                       {"id":1,"object":"assignment","url":"https://api.wanikani.com/v2/assignments/1",
                        "data_updated_at":"2026-01-01T00:00:00.000000Z",
                        "data":{"created_at":"2026-01-01T00:00:00.000000Z","subject_id":440,
                        "subject_type":"kanji","srs_stage":1,"hidden":false}}]}"""
                )
                path.startsWith("/review_statistics") -> emptyCollection("review_statistic")
                path.startsWith("/level_progressions") -> emptyCollection("level_progression")
                else -> emptyResponse(404)
            }
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun emptyEverything(objectType: String = "assignment") = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path.orEmpty()
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

    /**
     * The regression, stated as the number that matters: a resume moments after a sync must not
     * rewrite the assignments table.
     *
     * This is the "returned to the dashboard after a review" case — the one the harness caught
     * stalling for 272 ms.
     */
    @Test
    fun `a resume moments after a sync does not rewrite assignments`() = runTest {
        repositories.syncOrchestrator.syncAll(force = true) // establishes cursors
        repositories.writeLog.reset()

        repositories.syncOrchestrator.syncAllForResume()

        assertThat(repositories.writeLog.rowCountFor("assignments")).isEqualTo(0)
    }

    /**
     * And it must not make the request either — the point is to skip the work, not to fetch and then
     * discard it. A fetch of several thousand rows costs network, JSON decoding and TypeConverter
     * work even when the write is skipped.
     */
    @Test
    fun `a resume moments after a sync does not even fetch assignments`() = runTest {
        repositories.syncOrchestrator.syncAll(force = true)
        val before = server.requestCount

        repositories.syncOrchestrator.syncAllForResume()

        assertThat(server.requestCount).isEqualTo(before)
    }

    /**
     * The other half of the contract: a resume after a real gap *must* still refetch, or the cards
     * derived from the assignments table would trail the banner indefinitely. The window is short, not
     * absent.
     *
     * Simulated by clearing the recorded sync time rather than waiting, since the window is half a
     * minute and a test cannot sleep for it.
     */
    @Test
    fun `a resume after the window has elapsed refetches assignments`() = runTest {
        repositories.syncOrchestrator.syncAll(force = true)
        repositories.syncStateDao.clearAll() // as if the last sync were long ago
        repositories.writeLog.reset()

        repositories.syncOrchestrator.syncAllForResume()

        // The row is rewritten, which is what proves the window is a window and not a blanket skip.
        assertThat(repositories.writeLog.rowCountFor("assignments")).isEqualTo(1)
    }

    private fun emptyCollection(objectType: String) = jsonResponse(
        """{"object":"$objectType","url":"https://api.wanikani.com/v2/$objectType","data":[]}"""
    )
}
