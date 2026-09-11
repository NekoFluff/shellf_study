package com.crazyfluff.shellfstudy.shared.network.weblio

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WeblioApiTest {

    @Test
    fun getEntryReturnsTheRawResponseBodyText() = runTest {
        var requestedUrl: String? = null
        val engine = MockEngine { request ->
            requestedUrl = request.url.toString()
            respond(
                content = "<html>pitch accent entry</html>",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", listOf("text/html"))
            )
        }
        val api: WeblioApi = KtorWeblioApi(createWeblioHttpClient(engine = engine))

        val entry = api.getEntry("漢字")

        assertEquals(WeblioEntry.Page("<html>pitch accent entry</html>"), entry)
        assertTrue(requireNotNull(requestedUrl).startsWith("https://www.weblio.jp/content"))
    }

    @Test
    fun getEntryMapsNotFoundToNotFoundInsteadOfThrowing() = runTest {
        val engine = MockEngine { respond(content = "not found", status = HttpStatusCode.NotFound) }
        val api: WeblioApi = KtorWeblioApi(createWeblioHttpClient(engine = engine))

        assertEquals(WeblioEntry.NotFound, api.getEntry("無い語"))
    }

    @Test
    fun getEntryStillThrowsForOtherErrorStatuses() = runTest {
        val engine = MockEngine { respond(content = "boom", status = HttpStatusCode.InternalServerError) }
        val api: WeblioApi = KtorWeblioApi(createWeblioHttpClient(engine = engine))

        // Only a 404 is weblio's definitive answer; a server error stays a retryable failure.
        assertFailsWith<ServerResponseException> { api.getEntry("漢字") }
    }
}
