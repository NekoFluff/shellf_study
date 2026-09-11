package com.crazyfluff.shellfstudy.shared.network.weblio

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode

private const val WEBLIO_BASE_URL = "https://www.weblio.jp/"
private const val CONNECT_TIMEOUT_MILLIS = 10_000L
private const val READ_TIMEOUT_MILLIS = 60_000L

/**
 * What weblio had to say about a query. The distinction matters to the caller
 * ([com.crazyfluff.shellfstudy.shared.data.PitchAccentRepository]): weblio answering "no entry" is a
 * *definitive* result that is cached as a confirmed absence, while anything thrown (network error,
 * 5xx, other 4xx) leaves the answer still unknown and retryable.
 */
sealed interface WeblioEntry {
    data class Page(val html: String) : WeblioEntry

    /** weblio has no entry for this query — a definitive answer, not a transient failure. */
    data object NotFound : WeblioEntry
}

/** A separate small client for weblio.jp — a different host from the WaniKani API, no auth needed. */
interface WeblioApi {
    /**
     * Looks [query] up on weblio. Yields [WeblioEntry.Page] with the raw HTML body on success and
     * [WeblioEntry.NotFound] when weblio answers 404, meaning the entry genuinely does not exist.
     * Every other outcome — a non-404 HTTP status, a server error, or an IO/engine failure — throws,
     * so the caller can treat it as a transient failure worth retrying.
     */
    suspend fun getEntry(query: String): WeblioEntry
}

class KtorWeblioApi(private val httpClient: HttpClient) : WeblioApi {
    override suspend fun getEntry(query: String): WeblioEntry =
        try {
            WeblioEntry.Page(
                httpClient.get("${WEBLIO_BASE_URL}content") {
                    parameter("query", query)
                }.bodyAsText()
            )
        } catch (e: ClientRequestException) {
            // expectSuccess = true turns every non-2xx into an exception; only a 404 carries the
            // "this entry does not exist" answer, so every other status keeps propagating.
            if (e.response.status == HttpStatusCode.NotFound) WeblioEntry.NotFound else throw e
        }
}

/** Builds the [HttpClient] backing [KtorWeblioApi]. Pass [engine] (e.g. a MockEngine) in tests. */
fun createWeblioHttpClient(engine: HttpClientEngine? = null): HttpClient {
    val config: HttpClientConfig<*>.() -> Unit = {
        // Matches Retrofit's default suspend-fun behavior: throw on non-2xx rather than returning
        // the error body as if it were the scraped page.
        expectSuccess = true
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
            requestTimeoutMillis = READ_TIMEOUT_MILLIS
        }
    }
    return if (engine != null) HttpClient(engine, config) else HttpClient(config)
}
