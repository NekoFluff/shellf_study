package com.crazyfluff.shellfstudy.shared.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FriendStatsHelperTest {

    // buildTimeline

    @Test
    fun buildTimeline_emptyList_returnsEmptyList() {
        assertEquals(emptyList(), buildTimeline(emptyList()))
    }

    @Test
    fun buildTimeline_singleProgression_returnsDayZero() {
        val result = buildTimeline(listOf(1 to "2026-01-01T00:00:00.000Z"))
        assertEquals(1, result.size)
        assertEquals(0, result.first().daysSinceStart)
        assertEquals(1, result.first().level)
    }

    @Test
    fun buildTimeline_skipsEntriesWithUnparseableTimestamp() {
        val progressions = listOf(
            1 to "2026-01-01T00:00:00.000Z",
            2 to "not-a-timestamp",
            3 to "2026-01-31T00:00:00.000Z"
        )
        val result = buildTimeline(progressions)
        // "not-a-timestamp" entry should be skipped; the other two survive
        assertEquals(2, result.size)
        assertEquals(0, result[0].daysSinceStart)
        assertEquals(30, result[1].daysSinceStart)
    }

    @Test
    fun buildTimeline_returnsNullForFirstEntryWhenStartTimestampUnparseable() {
        // If the very first timestamp is bad, startMillis is null and the whole list is empty
        val result = buildTimeline(listOf(1 to "bad", 2 to "2026-01-10T00:00:00.000Z"))
        assertEquals(emptyList(), result)
    }

    @Test
    fun buildTimeline_computesDaysSinceStartCorrectly() {
        val progressions = listOf(
            1 to "2026-01-01T00:00:00.000Z",
            2 to "2026-01-08T00:00:00.000Z",  // 7 days later
            3 to "2026-02-01T00:00:00.000Z"   // 31 days later
        )
        val result = buildTimeline(progressions)
        assertEquals(3, result.size)
        assertEquals(0, result[0].daysSinceStart)
        assertEquals(7, result[1].daysSinceStart)
        assertEquals(31, result[2].daysSinceStart)
        assertEquals(1, result[0].level)
        assertEquals(2, result[1].level)
        assertEquals(3, result[2].level)
    }

    // computeWindowedCounts — already exercised indirectly via ActivityBucketCalculatorTest,
    // so only the edge cases for the windowed-count branch are needed here.

    @Test
    fun computeWindowedCounts_emptyList_returnsAllZeros() {
        val now = 1_000_000_000_000L
        val result = computeWindowedCounts(emptyList(), now)
        assertEquals(0, result.today)
        assertEquals(0, result.week)
        assertEquals(0, result.month)
        assertEquals(0, result.year)
        assertEquals(0, result.allTime)
    }

    @Test
    fun computeWindowedCounts_skipsNullAndUnparseableTimestamps() {
        val now = 1_000_000_000_000L
        val result = computeWindowedCounts(listOf(null, "not-a-timestamp"), now)
        assertEquals(0, result.allTime)
    }

    @Test
    fun computeWindowedCounts_countsAllTimeRegardlessOfAge() {
        // Two timestamps — one recent, one 10 years ago — should both count toward allTime.
        val nowMillis = 1_750_000_000_000L  // some fixed point ~2025
        val recent = "2025-01-01T00:00:00.000Z"
        val old = "2015-01-01T00:00:00.000Z"
        val result = computeWindowedCounts(listOf(recent, old), nowMillis)
        assertEquals(2, result.allTime)
    }
}
