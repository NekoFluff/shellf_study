package com.crazyfluff.shellfstudy.shared.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The `IN (:ids)` helper. SQLite's `SQLITE_MAX_VARIABLE_NUMBER` is 999 before 3.32 — which is what
 * Android 28 through 32 ship — so a query binding a thousand ids fails outright rather than being
 * merely slow. Android 33+ raised it, which is why this only bites on older devices with a large
 * backlog: precisely the combination a test suite on a modern emulator would never produce.
 */
class ChunkedIdsTest {

    @Test
    fun `a list over the limit is split into queries that fit it`() = runTest {
        val ids = (1..2_000L).toList()
        val queried = mutableListOf<List<Long>>()

        val rows = chunkedIds(ids) { chunk ->
            queried += chunk
            flowOf(chunk)
        }.first()

        assertTrue(queried.all { it.size <= 900 }, "a chunk exceeded the bound-variable limit: ${queried.map { it.size }}")
        assertEquals(3, queried.size, "2000 ids at 900 per query should be three queries")
        assertEquals(ids, rows, "chunking must not reorder or drop ids")
    }

    @Test
    fun `a list that already fits is queried once without chunking`() = runTest {
        var queryCount = 0

        val rows = chunkedIds(listOf(1L, 2L, 3L)) { chunk ->
            queryCount++
            flowOf(chunk)
        }.first()

        assertEquals(1, queryCount)
        assertEquals(listOf(1L, 2L, 3L), rows)
    }

    @Test
    fun `no ids means no query at all`() = runTest {
        var queried = false

        val rows = chunkedIds(emptyList<Long>()) {
            queried = true
            flowOf(it)
        }.first()

        assertTrue(rows.isEmpty())
        assertTrue(!queried, "an empty id list must not reach the database")
    }

    @Test
    fun `the one-shot variant chunks the same way`() = runTest {
        val ids = (1..1_800L).toList()
        val queried = mutableListOf<List<Long>>()

        val rows = chunkedIdsOnce(ids) { chunk ->
            queried += chunk
            chunk
        }

        assertEquals(2, queried.size)
        assertEquals(ids, rows)
    }
}
