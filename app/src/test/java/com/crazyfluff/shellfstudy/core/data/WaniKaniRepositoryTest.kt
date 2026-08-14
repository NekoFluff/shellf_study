package com.crazyfluff.shellfstudy.core.data

import com.crazyfluff.shellfstudy.shared.data.ApiResult

import com.crazyfluff.shellfstudy.shared.data.model.ReviewGrade
import com.crazyfluff.shellfstudy.fakes.TestRepositories
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.crazyfluff.shellfstudy.fakes.jsonResponse
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class WaniKaniRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repositories: TestRepositories
    private val repository get() = repositories.waniKaniRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repositories = buildTestRepositories(server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchUser returns success with parsed user`() = runTest {
        server.enqueue(jsonResponse(USER_JSON))

        val result = repository.fetchUser()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val user = (result as ApiResult.Success).data
        assertThat(user.username).isEqualTo("durtle_fan")
        assertThat(user.level).isEqualTo(12)
    }

    @Test
    fun `fetchUser maps 401 to invalid token error`() = runTest {
        server.enqueue(jsonResponse("""{"error":"Unauthorized"}""", 401))

        val result = repository.fetchUser()

        assertThat(result).isInstanceOf(ApiResult.Error::class.java)
        assertThat((result as ApiResult.Error).message).contains("Invalid API token")
    }

    @Test
    fun `fetchDashboardSummary sums available subject ids`() = runTest {
        server.enqueue(jsonResponse(SUMMARY_JSON))

        val result = repository.fetchDashboardSummary()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val summary = (result as ApiResult.Success).data
        assertThat(summary.lessonCount).isEqualTo(2)
        assertThat(summary.reviewCount).isEqualTo(3)
    }

    @Test
    fun `submitReview posts incorrect counts derived from grade`() = runTest {
        server.enqueue(jsonResponse(REVIEW_RESULT_JSON))

        val result = repository.submitReview(
            assignmentId = 555,
            grade = ReviewGrade(meaningCorrect = true, readingCorrect = false)
        )

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.body.readUtf8()).contains("\"incorrect_reading_answers\":1")
    }

    @Test
    fun `submitReview returns the parsed result without any local DB side effect`() = runTest {
        server.enqueue(jsonResponse(REVIEW_RESULT_JSON))

        val result = repository.submitReview(assignmentId = 555, grade = ReviewGrade(meaningCorrect = true, readingCorrect = false))

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val data = (result as ApiResult.Success).data
        assertThat(data.assignmentId).isEqualTo(555)
        assertThat(data.endingSrsStage).isEqualTo(2)
    }

    private companion object {
        val USER_JSON = """
            {
              "object": "user",
              "url": "https://api.wanikani.com/v2/user",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "id": "abc-123",
                "username": "durtle_fan",
                "level": 12,
                "profile_url": "https://www.wanikani.com/users/durtle_fan",
                "started_at": "2020-01-01T00:00:00.000000Z"
              }
            }
        """.trimIndent()

        val SUMMARY_JSON = """
            {
              "object": "report",
              "url": "https://api.wanikani.com/v2/summary",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "lessons": [{"available_at": "2026-01-01T00:00:00.000000Z", "subject_ids": [1, 2]}],
                "reviews": [{"available_at": "2026-01-01T00:00:00.000000Z", "subject_ids": [3, 4, 5]}]
              }
            }
        """.trimIndent()

        val REVIEW_RESULT_JSON = """
            {
              "id": 1,
              "object": "review",
              "url": "https://api.wanikani.com/v2/reviews/1",
              "data_updated_at": "2026-01-01T00:00:00.000000Z",
              "data": {
                "assignment_id": 555,
                "subject_id": 440,
                "starting_srs_stage": 3,
                "ending_srs_stage": 2,
                "incorrect_meaning_answers": 0,
                "incorrect_reading_answers": 1,
                "created_at": "2026-01-01T00:00:00.000000Z"
              }
            }
        """.trimIndent()
    }
}
