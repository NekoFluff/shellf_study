package com.crazyfluff.shellfstudy.shared.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WaniKaniApiTest {

    private fun apiCapturing(
        response: String,
        onRequest: (HttpRequestData) -> Unit = {}
    ): WaniKaniApi {
        val engine = MockEngine { request ->
            onRequest(request)
            respond(
                content = response,
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", listOf("application/json"))
            )
        }
        val client = createWaniKaniHttpClient(tokenProvider = { "test-token" }, engine = engine)
        return WaniKaniApi(client)
    }

    private fun HttpRequestData.bodyAsText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    @Test
    fun getUserDeserializesTheEnvelopeAndData() = runTest {
        val api = apiCapturing(
            """{"object":"user","url":"https://api.wanikani.com/v2/user","data":{"id":"u1","username":"alex","level":5,"profile_url":"https://x","started_at":"2020-01-01"}}"""
        )

        val response = api.getUser()

        assertEquals("alex", response.data.username)
        assertEquals(5, response.data.level)
    }

    @Test
    fun getAssignmentsSendsRepeatedQueryParamsForListFilters() = runTest {
        var requestedUrl: String? = null
        val api = apiCapturing(
            response = """{"object":"collection","url":"https://api.wanikani.com/v2/assignments","data":[]}""",
            onRequest = { requestedUrl = it.url.toString() }
        )

        api.getAssignments(ids = listOf(1L, 2L, 3L), levels = listOf(4, 5))

        val url = requireNotNull(requestedUrl)
        assertEquals(listOf("1", "2", "3"), extractQueryValues(url, "ids"))
        assertEquals(listOf("4", "5"), extractQueryValues(url, "levels"))
    }

    @Test
    fun getAssignmentsPageRequestsTheGivenAbsoluteUrlDirectly() = runTest {
        var requestedUrl: String? = null
        val api = apiCapturing(
            response = """{"object":"collection","url":"https://api.wanikani.com/v2/assignments?page=2","data":[]}""",
            onRequest = { requestedUrl = it.url.toString() }
        )

        api.getAssignmentsPage("https://api.wanikani.com/v2/assignments?page=2")

        assertEquals("https://api.wanikani.com/v2/assignments?page=2", requestedUrl)
    }

    @Test
    fun submitReviewPostsAJsonBodyMatchingTheRequest() = runTest {
        var capturedBody: String? = null
        val api = apiCapturing(
            response = """{"id":1,"object":"review","url":"https://api.wanikani.com/v2/reviews/1","data":{"assignment_id":10,"subject_id":20,"starting_srs_stage":3,"ending_srs_stage":4,"incorrect_meaning_answers":0,"incorrect_reading_answers":1,"created_at":"2020-01-01"}}""",
            onRequest = { capturedBody = it.bodyAsText() }
        )

        val result = api.submitReview(
            ReviewSubmissionRequest(
                ReviewSubmissionBody(assignmentId = 10, incorrectMeaningAnswers = 0, incorrectReadingAnswers = 1)
            )
        )

        assertEquals(4, result.data.endingSrsStage)
        val body = requireNotNull(capturedBody)
        assertEquals(true, body.contains("\"assignment_id\":10"))
        assertEquals(true, body.contains("\"incorrect_reading_answers\":1"))
    }

    @Test
    fun startAssignmentWrapsTheRequestUnderAnAssignmentKey() = runTest {
        // A non-default started_at forces kotlinx.serialization to actually encode the
        // "assignment" wrapper — with the request's default value, encodeDefaults=false (matching
        // the original Retrofit setup's Json config) omits it and the body is legitimately "{}".
        var capturedBody: String? = null
        val api = apiCapturing(
            response = """{"id":1,"object":"assignment","url":"https://api.wanikani.com/v2/assignments/1","data":{"created_at":"2020-01-01","subject_id":1,"subject_type":"kanji","srs_stage":1}}""",
            onRequest = { capturedBody = it.bodyAsText() }
        )

        api.startAssignment(
            assignmentId = 1,
            request = StartAssignmentRequest(StartAssignmentBody(startedAt = "2020-06-01T00:00:00Z"))
        )

        val body = requireNotNull(capturedBody)
        assertEquals(true, body.contains("\"assignment\""))
        assertEquals(true, body.contains("\"started_at\":\"2020-06-01T00:00:00Z\""))
    }

    private fun extractQueryValues(url: String, key: String): List<String> {
        val query = url.substringAfter("?", missingDelimiterValue = "")
        return query.split("&")
            .filter { it.startsWith("$key=") }
            .map { it.substringAfter("=") }
    }
}
