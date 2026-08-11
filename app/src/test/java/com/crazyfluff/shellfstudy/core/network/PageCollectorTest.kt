package com.crazyfluff.shellfstudy.core.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PageCollectorTest {

    @Test
    fun `collectAllPages returns the first page's items when there is only one page`() = runTest {
        val page = collectionResponse(items = listOf(1, 2, 3), nextUrl = null)

        val result = collectAllPages(firstPage = { page }, nextPage = { error("should not be called") })

        assertThat(result.map { it.data }).containsExactly(1, 2, 3).inOrder()
    }

    @Test
    fun `collectAllPages follows next_url until it is exhausted, concatenating items in order`() = runTest {
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

        assertThat(result.map { it.data }).containsExactly(1, 2, 3, 4, 5).inOrder()
        assertThat(requestedUrls).containsExactly("page2", "page3").inOrder()
    }

    @Test
    fun `collectAllPages returns an empty list when the first page has no items`() = runTest {
        val page = collectionResponse(items = emptyList(), nextUrl = null)

        val result = collectAllPages(firstPage = { page }, nextPage = { error("should not be called") })

        assertThat(result).isEmpty()
    }

    private fun collectionResponse(items: List<Int>, nextUrl: String?): WkCollectionResponse<Int> =
        WkCollectionResponse(
            objectType = "test",
            url = "https://api.wanikani.com/v2/test",
            pages = nextUrl?.let { WkPages(nextUrl = it) },
            data = items.map { WkResourceItem(id = it.toLong(), objectType = "test", url = "https://api.wanikani.com/v2/test/$it", data = it) }
        )
}
