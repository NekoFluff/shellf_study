package com.crazyfluff.shellfstudy.shared.network.weblio

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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

        val html = api.getEntry("漢字")

        assertEquals("<html>pitch accent entry</html>", html)
        assertTrue(requireNotNull(requestedUrl).startsWith("https://www.weblio.jp/content"))
    }
}
