package com.crazyfluff.shellfstudy.shared.network.historysync

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Where the history-sync Worker (`backend/history-sync/`) is deployed, without a trailing slash. Set
 * this to "" to make history sync unavailable and hide its settings. Renaming the account's
 * workers.dev subdomain changes this URL, and installed builds keep calling the old one.
 */
const val HISTORY_SYNC_BASE_URL = "https://sync-shellf-study.nekofluff.workers.dev"

@Serializable
data class HistoryAuthRequest(val wkToken: String)

@Serializable
data class HistoryAuthResponse(val token: String, val userId: String, val expiresAtMs: Long)

/** One synced record. The backend stores [payload] without reading it; the app owns its shape. */
@Serializable
data class HistoryRecordDto(val type: String, val recordId: String, val payload: JsonObject)

@Serializable
data class HistoryPushRequest(val records: List<HistoryRecordDto>)

@Serializable
data class HistoryPage(val records: List<HistoryRecordDto>, val cursor: Long, val hasMore: Boolean)

/** The history-sync backend's four routes. Every call throws on a non-2xx, like
 *  [com.crazyfluff.shellfstudy.shared.network.WaniKaniApi]. */
class HistorySyncApi(private val client: HttpClient, private val baseUrl: String) {

    /** Proves who [wkToken] belongs to and returns a token for the other routes. */
    suspend fun auth(wkToken: String): HistoryAuthResponse =
        client.post("$baseUrl/v1/auth") {
            contentType(ContentType.Application.Json)
            setBody(HistoryAuthRequest(wkToken))
        }.body()

    suspend fun push(token: String, records: List<HistoryRecordDto>) {
        client.post("$baseUrl/v1/history") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(HistoryPushRequest(records))
        }
    }

    suspend fun pull(token: String, after: Long, limit: Int): HistoryPage =
        client.get("$baseUrl/v1/history") {
            bearerAuth(token)
            parameter("after", after)
            parameter("limit", limit)
        }.body()

    suspend fun deleteAll(token: String) {
        client.delete("$baseUrl/v1/me") { bearerAuth(token) }
    }
}

/**
 * A client of its own rather than the WaniKani one: that one attaches the WaniKani token to every
 * request, and the token has no business going anywhere but `/v1/auth`'s body. No logging plugin,
 * for the same reason. Pass [engine] (e.g. a MockEngine) in tests.
 */
fun createHistorySyncHttpClient(json: Json, engine: HttpClientEngine? = null): HttpClient {
    val config: HttpClientConfig<*>.() -> Unit = {
        expectSuccess = true
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
        install(ContentNegotiation) { json(json) }
    }
    return if (engine != null) HttpClient(engine, config) else HttpClient(config)
}
