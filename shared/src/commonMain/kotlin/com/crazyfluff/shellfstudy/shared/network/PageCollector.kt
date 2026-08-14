package com.crazyfluff.shellfstudy.shared.network

/**
 * Follows a paginated WaniKani collection endpoint's `pages.next_url` until exhausted, accumulating
 * every item across all pages. Kept per-resource-typed (called with each endpoint's own typed
 * lambdas) rather than one fully generic method, since kotlinx.serialization needs a concrete
 * reified response type per call.
 */
suspend fun <T> collectAllPages(
    firstPage: suspend () -> WkCollectionResponse<T>,
    nextPage: suspend (String) -> WkCollectionResponse<T>
): List<WkResourceItem<T>> {
    val results = mutableListOf<WkResourceItem<T>>()
    var response = firstPage()
    results += response.data
    var nextUrl = response.pages?.nextUrl
    while (nextUrl != null) {
        response = nextPage(nextUrl)
        results += response.data
        nextUrl = response.pages?.nextUrl
    }
    return results
}
