package com.crazyfluff.shellfstudy.shared.network

/**
 * Thrown when a paginated collection cannot be walked to its end — a cursor that repeats, more pages
 * than the API can plausibly return, or a cursor pointing off-origin.
 *
 * Distinct from a connectivity failure on purpose: the sync layer reports these differently, and a
 * server that hands out a looping cursor must not look like "check your connection" to the user.
 */
class PaginationException(message: String) : Exception(message)

/**
 * Follows a paginated WaniKani collection endpoint's `pages.next_url` until exhausted, accumulating
 * every item across all pages. Kept per-resource-typed (called with each endpoint's own typed
 * lambdas) rather than one fully generic method, since kotlinx.serialization needs a concrete
 * reified response type per call.
 *
 * The walk is bounded. A cursor that repeats is a loop — WaniKani's cursor is `page_after_id`, so the
 * same URL twice means the same page was served twice — and following it would accumulate a copy of
 * that page forever. `MAX_PAGES` is the second guard, for a cursor that keeps advancing without ever
 * being null. Both fail loudly rather than returning a truncated collection: a silently short list
 * would be written to the local cache as if it were complete.
 */
suspend fun <T> collectAllPages(
    firstPage: suspend () -> WkCollectionResponse<T>,
    nextPage: suspend (String) -> WkCollectionResponse<T>
): List<WkResourceItem<T>> {
    val results = mutableListOf<WkResourceItem<T>>()
    var response = firstPage()
    results += response.data
    var nextUrl = response.pages?.nextUrl

    val followed = mutableSetOf<String>()
    while (nextUrl != null) {
        if (!followed.add(nextUrl)) {
            throw PaginationException("WaniKani repeated a page cursor ($nextUrl); refusing to loop.")
        }
        if (followed.size > MAX_PAGES) {
            throw PaginationException("WaniKani returned more than $MAX_PAGES pages; refusing to continue.")
        }
        response = nextPage(nextUrl)
        results += response.data
        nextUrl = response.pages?.nextUrl
    }
    return results
}

/**
 * Far above any real collection — subjects, the largest, are 9k-odd items at 1000 a page, so ten
 * pages — and low enough that a runaway cursor is caught in a bound a test can reach.
 */
internal const val MAX_PAGES = 100
