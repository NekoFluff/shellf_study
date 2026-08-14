package com.crazyfluff.shellfstudy.shared.network

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PageCollectorTest {

    @Test
    fun collectAllPagesReturnsFirstPageItemsWhenThereIsOnlyOnePage() = runTest {
        val page = collectionResponse(items = listOf(1, 2, 3), nextUrl = null)

        val result = collectAllPages(firstPage = { page }, nextPage = { error("should not be called") })

        assertEquals(listOf(1, 2, 3), result.map { it.data })
    }

    @Test
    fun collectAllPagesFollowsNextUrlUntilExhaustedConcatenatingItemsInOrder() = runTest {
        val firstPage = collectionResponse(items = listOf(1, 2), nextUrl = "page2")
        val secondPage = collectionResponse(items = listOf(3, 4), nextUrl = "page3")
        val thirdPage = collectionResponse(items = listOf(5), nextUrl = null)
        val requestedUrls = mutableListOf<String>()

        val result = collectAllPages(
            firstPage = { firstPage },
            nextPage = { url ->
                requestedUrls += url
                when (url) {
                    "page2" -> secondPage
                    "page3" -> thirdPage
                    else -> error("unexpected url: $url")
                }
            }
        )

        assertEquals(listOf(1, 2, 3, 4, 5), result.map { it.data })
        assertEquals(listOf("page2", "page3"), requestedUrls)
    }

    @Test
    fun collectAllPagesReturnsEmptyListWhenFirstPageHasNoItems() = runTest {
        val page = collectionResponse(items = emptyList(), nextUrl = null)

        val result = collectAllPages(firstPage = { page }, nextPage = { error("should not be called") })

        assertTrue(result.isEmpty())
    }

    private fun collectionResponse(items: List<Int>, nextUrl: String?): WkCollectionResponse<Int> =
        WkCollectionResponse(
            objectType = "test",
            url = "https://api.wanikani.com/v2/test",
            pages = nextUrl?.let { WkPages(nextUrl = it) },
            data = items.map { WkResourceItem(id = it.toLong(), objectType = "test", url = "https://api.wanikani.com/v2/test/$it", data = it) }
        )
}
