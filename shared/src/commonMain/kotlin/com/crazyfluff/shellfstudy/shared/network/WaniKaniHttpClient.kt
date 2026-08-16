package com.crazyfluff.shellfstudy.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal const val WANIKANI_BASE_URL = "https://api.wanikani.com/v2/"
private const val WANIKANI_REVISION = "20170710"

fun waniKaniJson(): Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

/**
 * Attaches the stored WaniKani API token as a Bearer credential, plus the required
 * Wanikani-Revision header, to every request — the Ktor equivalent of the old OkHttp
 * AuthInterceptor, as a request-hook plugin so the (suspend) token lookup can run inline.
 */
private fun HttpClientConfig<*>.installWaniKaniAuthHeaders(tokenProvider: AuthTokenProvider) {
    install(
        createClientPlugin("WaniKaniAuthHeaders") {
            onRequest { request, _ ->
                request.headers.append("Wanikani-Revision", WANIKANI_REVISION)
                tokenProvider.currentToken()?.let { token ->
                    if (token.isNotBlank()) {
                        request.headers.append("Authorization", "Bearer $token")
                    }
                }
            }
        }
    )
}

/** Creates a throw-away [WaniKaniApi] bound to a single fixed token — for friend stats fetches.
 *  Do not cache; create fresh per fetch (each friend is fetched at most once per 30 minutes). */
fun createFriendWaniKaniApi(token: String, json: Json = waniKaniJson()): WaniKaniApi =
    WaniKaniApi(createWaniKaniHttpClient(tokenProvider = AuthTokenProvider { token }, json = json))

/** Builds the [HttpClient] backing [WaniKaniApi]. Pass [engine] (e.g. a MockEngine) in tests. */
fun createWaniKaniHttpClient(
    tokenProvider: AuthTokenProvider,
    json: Json = waniKaniJson(),
    engine: HttpClientEngine? = null
): HttpClient {
    val config: HttpClientConfig<*>.() -> Unit = {
        // Matches Retrofit's default suspend-fun behavior: throw on non-2xx rather than returning
        // the error body as if it were a success (SafeApiCall.kt catches the resulting exception).
        expectSuccess = true
        install(ContentNegotiation) { json(json) }
        install(Logging) { level = LogLevel.INFO }
        installWaniKaniAuthHeaders(tokenProvider)
    }
    return if (engine != null) HttpClient(engine, config) else HttpClient(config)
}
