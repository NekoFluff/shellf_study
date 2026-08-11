package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.core.network.weblio.WeblioApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

/**
 * In-memory stand-in for [WeblioApi] — the real one hits weblio.jp over the network. A query with
 * no configured page throws, the same as a real network failure, so tests can distinguish "scraped
 * successfully but weblio has no entry" (an empty configured page) from "the fetch itself failed."
 */
class FakeWeblioApi(
    private val pagesByQuery: Map<String, String> = emptyMap()
) : WeblioApi {
    override suspend fun getEntry(query: String): ResponseBody =
        pagesByQuery[query]?.toResponseBody("text/html".toMediaType())
            ?: throw IOException("no fake response configured for query: $query")
}
