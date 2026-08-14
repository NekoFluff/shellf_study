package com.crazyfluff.shellfstudy.shared.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WaniKaniAuthHeadersTest {

    private fun clientCapturing(
        tokenProvider: AuthTokenProvider,
        onRequestHeaders: (auth: String?, revision: String?) -> Unit
    ) = createWaniKaniHttpClient(
        tokenProvider = tokenProvider,
        engine = MockEngine { request ->
            onRequestHeaders(request.headers[HttpHeaders.Authorization], request.headers["Wanikani-Revision"])
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
    )

    @Test
    fun attachesBearerTokenWhenOneIsStored() = runTest {
        var capturedAuth: String? = null
        val client = clientCapturing(tokenProvider = { "abc123" }) { auth, _ -> capturedAuth = auth }

        client.get("${WANIKANI_BASE_URL}user")

        assertEquals("Bearer abc123", capturedAuth)
    }

    @Test
    fun omitsAuthorizationHeaderWhenNoTokenIsStored() = runTest {
        var capturedAuth: String? = null
        val client = clientCapturing(tokenProvider = { null }) { auth, _ -> capturedAuth = auth }

        client.get("${WANIKANI_BASE_URL}user")

        assertNull(capturedAuth)
    }

    @Test
    fun alwaysAttachesTheWaniKaniRevisionHeader() = runTest {
        var capturedRevision: String? = null
        val client = clientCapturing(tokenProvider = { null }) { _, revision -> capturedRevision = revision }

        client.get("${WANIKANI_BASE_URL}user")

        assertEquals("20170710", capturedRevision)
    }
}
