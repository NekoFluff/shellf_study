package com.crazyfluff.shellfstudy.fakes

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest

/**
 * An in-memory stand-in for the history-sync Worker (`backend/history-sync/`), behind MockWebServer,
 * so several simulated devices can sync through one "server" and see each other's history. It keeps
 * the real backend's contract: per-user isolation, store-once by (type, recordId), and a server-assigned
 * sequence as the pull cursor.
 *
 * @param waniKaniUsers WaniKani tokens `/v1/auth` accepts, and the user id each belongs to.
 */
class FakeHistoryBackend(private val waniKaniUsers: Map<String, String>) : Dispatcher() {

    data class StoredRecord(
        val seq: Long,
        val userId: String,
        val type: String,
        val recordId: String,
        val payload: JsonObject
    )

    private val records = mutableListOf<StoredRecord>()
    private val issuedTokens = mutableMapOf<String, String>()
    private var nextSeq = 1L

    var authCalls = 0
        private set

    /** The record count of every accepted push, in order. */
    val pushSizes = mutableListOf<Int>()

    /** The `after` of every pull, in order. */
    val pullCursors = mutableListOf<Long>()

    /** Caps each page below the client's limit, so a test can make a pull span several pages. */
    var pageSize = Int.MAX_VALUE

    /** Positions in [pullCursors] (0 = the first pull recorded) whose request is answered with a 500. */
    val failingPullIndexes = mutableSetOf<Int>()

    @Synchronized
    fun recordsFor(userId: String): List<StoredRecord> = records.filter { it.userId == userId }

    /** Stores a record directly, as if some other build had pushed it. */
    @Synchronized
    fun seed(userId: String, type: String, recordId: String, payload: JsonObject) {
        records += StoredRecord(nextSeq++, userId, type, recordId, payload)
    }

    /** Invalidates every issued token, as rotating the signing secret would. */
    @Synchronized
    fun rotateSecret() = issuedTokens.clear()

    @Synchronized
    override fun dispatch(request: RecordedRequest): MockResponse {
        val route = "${request.method} ${request.url.encodedPath}"
        val userId = request.headers["Authorization"]?.removePrefix("Bearer ")?.let(issuedTokens::get)
        return when {
            route == "POST /v1/auth" -> auth(request)
            userId == null -> json(401, """{"error":"unauthorized"}""")
            route == "POST /v1/history" -> push(request, userId)
            route == "GET /v1/history" -> pull(request, userId)
            route == "DELETE /v1/me" -> {
                records.removeAll { it.userId == userId }
                MockResponse.Builder().code(204).build()
            }
            else -> json(404, """{"error":"not_found"}""")
        }
    }

    private fun auth(request: RecordedRequest): MockResponse {
        authCalls++
        val wkToken = body(request)["wkToken"]?.jsonPrimitive?.content
        val userId = waniKaniUsers[wkToken] ?: return json(401, """{"error":"invalid_wanikani_token"}""")
        val token = "sync-token-$authCalls"
        issuedTokens[token] = userId
        val expiresAtMs = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
        return json(200, """{"token":"$token","userId":"$userId","expiresAtMs":$expiresAtMs}""")
    }

    private fun push(request: RecordedRequest, userId: String): MockResponse {
        val incoming = body(request)["records"]!!.jsonArray
        pushSizes += incoming.size
        incoming.forEach { element ->
            val record = element.jsonObject
            val type = record["type"]!!.jsonPrimitive.content
            val recordId = record["recordId"]!!.jsonPrimitive.content
            if (records.none { it.userId == userId && it.type == type && it.recordId == recordId }) {
                records += StoredRecord(nextSeq++, userId, type, recordId, record["payload"]!!.jsonObject)
            }
        }
        return json(200, """{"accepted":${incoming.size}}""")
    }

    private fun pull(request: RecordedRequest, userId: String): MockResponse {
        val after = request.url.queryParameter("after")!!.toLong()
        val limit = request.url.queryParameter("limit")!!.toInt()
        pullCursors += after
        if (pullCursors.lastIndex in failingPullIndexes) return json(500, """{"error":"internal"}""")
        val available = records.filter { it.userId == userId && it.seq > after }.sortedBy { it.seq }
        val page = available.take(minOf(limit, pageSize))
        val body = buildJsonObject {
            put("records", buildJsonArray {
                page.forEach { r ->
                    add(buildJsonObject {
                        put("type", r.type)
                        put("recordId", r.recordId)
                        put("payload", r.payload)
                    })
                }
            })
            put("cursor", page.lastOrNull()?.seq ?: after)
            put("hasMore", available.size > page.size)
        }
        return json(200, body.toString())
    }

    private fun body(request: RecordedRequest): JsonObject =
        Json.parseToJsonElement(request.body!!.utf8()).jsonObject

    private fun json(code: Int, body: String): MockResponse = jsonResponse(body, code)
}
