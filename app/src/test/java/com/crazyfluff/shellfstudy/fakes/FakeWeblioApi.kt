package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.shared.network.weblio.WeblioApi
import java.io.IOException

/**
 * In-memory stand-in for [WeblioApi] — the real one hits weblio.jp over the network. A query with
 * no configured page throws, the same as a real network failure, so tests can distinguish "scraped
 * successfully but weblio has no entry" (an empty configured page) from "the fetch itself failed."
 */
class FakeWeblioApi(
    private val pagesByQuery: Map<String, String> = emptyMap()
) : WeblioApi {
    override suspend fun getEntry(query: String): String =
        pagesByQuery[query] ?: throw IOException("no fake response configured for query: $query")
}
