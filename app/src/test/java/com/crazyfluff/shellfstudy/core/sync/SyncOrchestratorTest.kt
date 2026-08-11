package com.crazyfluff.shellfstudy.core.sync

import com.crazyfluff.shellfstudy.core.data.ApiResult
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
                    path.startsWith("/study_materials") -> emptyCollection("study_material")
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
            "/review_statistics", "/study_materials", "/level_progressions"
        )
    }

    @Test
    fun `syncAll returns Error when one resource fails, but the others still sync`() = runTest {
        reviewStatisticsShouldFail = true

        val result = repositories.syncOrchestrator.syncAll(force = true)

        assertThat(result).isInstanceOf(ApiResult.Error::class.java)
        assertThat((result as ApiResult.Error).message).contains("WaniKani API error (500)")
        // The other three parallel syncs (assignments, study materials, level progressions) still
        // ran to completion despite review_statistics failing.
        assertThat(pathsRequested()).containsAtLeast(
            "/spaced_repetition_systems", "/subjects", "/assignments",
            "/review_statistics", "/study_materials", "/level_progressions"
        )
    }

    private fun pathsRequested(): List<String> = requestedPaths.map { it.substringBefore('?') }

    private fun emptyCollection(objectType: String) = jsonResponse(
        """{"object":"$objectType","url":"https://api.wanikani.com/v2/$objectType","data":[]}"""
    )
}
