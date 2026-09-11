package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.shared.network.weblio.WeblioApi
import com.crazyfluff.shellfstudy.shared.network.weblio.WeblioEntry
import kotlinx.coroutines.CompletableDeferred
import java.io.IOException

/**
 * In-memory stand-in for [WeblioApi] — the real one hits weblio.jp over the network.
 *
 * Three outcomes, one per way the real client can answer:
 * - a query in [pagesByQuery] returns its configured page ([WeblioEntry.Page]);
 * - a query in [notFoundQueries] returns [WeblioEntry.NotFound] — weblio's definitive "no entry",
 *   which the repository caches as a confirmed absence;
 * - any other query throws [IOException], standing in for a network/HTTP failure (offline, 5xx, a
 *   non-404 error status), which the repository leaves retryable.
 *
 * A configured page always wins over [notFoundQueries], so a stale listing can't silently swallow a
 * page a test asked for.
 *
 * [gate], when given, parks every call until the test completes it — the only way a test can observe
 * the state *during* a scrape rather than only once it has returned.
 */
class FakeWeblioApi(
    private val pagesByQuery: Map<String, String> = emptyMap(),
    private val gate: CompletableDeferred<Unit>? = null,
    private val notFoundQueries: Set<String> = emptySet()
) : WeblioApi {
    override suspend fun getEntry(query: String): WeblioEntry {
        gate?.await()
        pagesByQuery[query]?.let { return WeblioEntry.Page(it) }
        if (query in notFoundQueries) return WeblioEntry.NotFound
        throw IOException("no fake response configured for query: $query")
    }
}
