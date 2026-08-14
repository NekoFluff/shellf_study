package com.crazyfluff.shellfstudy.shared.network.weblio

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText

private const val WEBLIO_BASE_URL = "https://www.weblio.jp/"
private const val CONNECT_TIMEOUT_MILLIS = 10_000L
private const val READ_TIMEOUT_MILLIS = 60_000L

/** A separate small client for weblio.jp — a different host from the WaniKani API, no auth needed. */
interface WeblioApi {
    suspend fun getEntry(query: String): String
}

class KtorWeblioApi(private val httpClient: HttpClient) : WeblioApi {
    override suspend fun getEntry(query: String): String =
        httpClient.get("${WEBLIO_BASE_URL}content") {
            parameter("query", query)
        }.bodyAsText()
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
