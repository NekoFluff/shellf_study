package com.crazyfluff.shellfstudy.shared.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

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

    @Test
    fun computeWindowedCounts_timestampExactlySevenDaysOld_isExcludedFromWeekBucket() {
        // The window comparison is a strict `<`, not `<=` — a timestamp exactly on the boundary
        // (7 days ago to the millisecond) must not count toward `week`, only `month`/`year`.
        val nowMillis = 1_750_000_000_000L
        val exactlySevenDaysAgo = Instant.fromEpochMilliseconds(nowMillis - 7 * 86_400_000L).toString()
        val result = computeWindowedCounts(listOf(exactlySevenDaysAgo), nowMillis)
        assertEquals(0, result.week)
        assertEquals(1, result.month)
        assertEquals(1, result.year)
    }

    // computeAvgDaysPerLevel

    @Test
    fun computeAvgDaysPerLevel_fewerThanTwoProgressions_returnsNull() {
        assertNull(computeAvgDaysPerLevel(emptyList()))
        assertNull(computeAvgDaysPerLevel(listOf(1 to "2026-01-01T00:00:00.000Z")))
    }

    @Test
    fun computeAvgDaysPerLevel_averagesTheIntervalsBetweenConsecutiveUnlocks() {
        val progressions = listOf(
            1 to "2026-01-01T00:00:00.000Z",
            2 to "2026-01-08T00:00:00.000Z", // 7 days after level 1
            3 to "2026-01-31T00:00:00.000Z"  // 23 days after level 2
        )
        // intervals: 7, 23 -> average 15
        assertEquals(15f, computeAvgDaysPerLevel(progressions))
    }

    @Test
    fun computeAvgDaysPerLevel_fewerThanTwoParseableTimestamps_returnsNull() {
        val progressions = listOf(1 to "bad", 2 to "also-bad", 3 to "2026-01-31T00:00:00.000Z")
        assertNull(computeAvgDaysPerLevel(progressions))
    }

    // buildStatsCore

    @Test
    fun buildStatsCore_computesAccuracyFromCorrectOverAttempts() {
        val core = buildStatsCore(
            burnedTimestamps = emptyList(),
            learnedTimestamps = emptyList(),
            burnedRawCount = 0,
            learnedRawCount = 0,
            totalCorrect = 80f,
            totalAttempts = 100f,
            sortedProgressions = emptyList(),
            nowMillis = 1_750_000_000_000L
        )
        assertEquals(0.8f, core.reviewAccuracy)
    }

    @Test
    fun buildStatsCore_zeroAttempts_reportsSentinelAccuracy() {
        val core = buildStatsCore(
            burnedTimestamps = emptyList(),
            learnedTimestamps = emptyList(),
            burnedRawCount = 0,
            learnedRawCount = 0,
            totalCorrect = 0f,
            totalAttempts = 0f,
            sortedProgressions = emptyList(),
            nowMillis = 1_750_000_000_000L
        )
        assertEquals(-1f, core.reviewAccuracy)
    }

    @Test
    fun buildStatsCore_rawCountOverridesAllTime_evenWhenATimestampFailsToParse() {
        // A raw item count of 3 with only 2 parseable timestamps — allTime must reflect the raw
        // count (3), not computeWindowedCounts' own count of successfully-parsed entries (2). This
        // is the exact network-path scenario buildStatsCore's rawCount params exist to handle.
        val core = buildStatsCore(
            burnedTimestamps = listOf("2025-01-01T00:00:00.000Z", "not-a-timestamp", "2025-06-01T00:00:00.000Z"),
            learnedTimestamps = emptyList(),
            burnedRawCount = 3,
            learnedRawCount = 0,
            totalCorrect = 0f,
            totalAttempts = 0f,
            sortedProgressions = emptyList(),
            nowMillis = 1_750_000_000_000L
        )
        assertEquals(3, core.burned.allTime)
    }

    @Test
    fun buildStatsCore_computesDaysSinceStartAndAvgDaysPerLevelFromSortedProgressions() {
        val startIso = "2025-01-01T00:00:00.000Z"
        val startMillis = Instant.parse(startIso).toEpochMilliseconds()
        val tenDaysLater = startMillis + 10 * 86_400_000L
        val progressions = listOf(
            1 to startIso,
            2 to "2025-01-08T00:00:00.000Z", // 7 days after level 1
            3 to "2025-01-31T00:00:00.000Z"  // 23 days after level 2
        )

        val core = buildStatsCore(
            burnedTimestamps = emptyList(),
            learnedTimestamps = emptyList(),
            burnedRawCount = 0,
            learnedRawCount = 0,
            totalCorrect = 0f,
            totalAttempts = 0f,
            sortedProgressions = progressions,
            nowMillis = tenDaysLater
        )

        assertEquals(10, core.daysSinceStart)
        assertEquals(15f, core.avgDaysPerLevel)
        assertEquals(3, core.timeline.size)
        assertEquals(0, core.timeline[0].daysSinceStart)
        assertEquals(7, core.timeline[1].daysSinceStart)
        assertEquals(30, core.timeline[2].daysSinceStart)
    }

    @Test
    fun buildStatsCore_noProgressions_reportsNullDaysSinceStartAndAvgDaysPerLevel() {
        val core = buildStatsCore(
            burnedTimestamps = emptyList(),
            learnedTimestamps = emptyList(),
            burnedRawCount = 0,
            learnedRawCount = 0,
            totalCorrect = 0f,
            totalAttempts = 0f,
            sortedProgressions = emptyList(),
            nowMillis = 1_750_000_000_000L
        )
        assertNull(core.daysSinceStart)
        assertNull(core.avgDaysPerLevel)
        assertEquals(emptyList(), core.timeline)
    }
}
