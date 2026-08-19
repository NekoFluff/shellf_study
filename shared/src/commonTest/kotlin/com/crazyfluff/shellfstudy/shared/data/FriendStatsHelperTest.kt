package com.crazyfluff.shellfstudy.shared.data

import kotlinx.datetime.TimeZone
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

    // computeWindowedCounts — derived from ActivityBuckets, so it's exercised mostly through
    // ActivityBucketCalculatorTest; these just confirm the summing/indexing itself.

    @Test
    fun computeWindowedCounts_emptyBuckets_returnsAllZeros() {
        val buckets = computeActivityBuckets(emptyList(), 1_000_000_000_000L)
        val result = computeWindowedCounts(buckets)
        assertEquals(0, result.today)
        assertEquals(0, result.week)
        assertEquals(0, result.month)
        assertEquals(0, result.year)
        assertEquals(0, result.allTime)
    }

    @Test
    fun computeWindowedCounts_skipsNullAndUnparseableTimestamps() {
        val buckets = computeActivityBuckets(listOf(null, "not-a-timestamp"), 1_000_000_000_000L)
        val result = computeWindowedCounts(buckets)
        assertEquals(0, result.allTime)
    }

    @Test
    fun computeWindowedCounts_countsAllTimeRegardlessOfAge() {
        // Two timestamps — one recent, one 10 years ago — should both count toward allTime.
        val nowMillis = 1_750_000_000_000L  // some fixed point ~2025
        val recent = "2025-01-01T00:00:00.000Z"
        val old = "2015-01-01T00:00:00.000Z"
        val buckets = computeActivityBuckets(listOf(recent, old), nowMillis)
        val result = computeWindowedCounts(buckets)
        assertEquals(2, result.allTime)
    }

    @Test
    fun computeWindowedCounts_timestampSevenCalendarDaysAgo_isExcludedFromWeekBucket() {
        // week counts the last 7 *calendar* days (today + 6 prior), matching computeActivityBuckets'
        // weekDays bucketing — a timestamp on the 7th-prior calendar day falls outside that range.
        val tz = TimeZone.UTC
        val nowMillis = Instant.parse("2025-06-15T12:00:00Z").toEpochMilliseconds()
        val sevenCalendarDaysAgo = "2025-06-08T12:00:00Z"
        val buckets = computeActivityBuckets(listOf(sevenCalendarDaysAgo), nowMillis, tz)
        val result = computeWindowedCounts(buckets)
        assertEquals(0, result.week)
        assertEquals(1, result.month)
        assertEquals(1, result.year)
    }

    @Test
    fun computeWindowedCounts_timestampSixCalendarDaysAgo_isIncludedInWeekBucket() {
        val tz = TimeZone.UTC
        val nowMillis = Instant.parse("2025-06-15T12:00:00Z").toEpochMilliseconds()
        val sixCalendarDaysAgo = "2025-06-09T00:00:01Z"
        val buckets = computeActivityBuckets(listOf(sixCalendarDaysAgo), nowMillis, tz)
        val result = computeWindowedCounts(buckets)
        assertEquals(1, result.week)
    }

    @Test
    fun computeWindowedCounts_alwaysAgreesWithSumOfActivityBuckets() {
        // Structural guarantee: computeWindowedCounts is derived from the same ActivityBuckets
        // rendered as the graph, so the table and graph can never disagree.
        val tz = TimeZone.UTC
        val nowMillis = Instant.parse("2025-06-15T12:00:00Z").toEpochMilliseconds()
        val timestamps = listOf(
            "2025-06-15T01:00:00Z", // today
            "2025-06-09T23:00:00Z", // 6 days ago
            "2025-06-08T23:00:00Z", // 7 days ago — outside the week window
            "2025-05-01T00:00:00Z"  // long ago
        )
        val buckets = computeActivityBuckets(timestamps, nowMillis, tz)
        val counts = computeWindowedCounts(buckets)
        assertEquals(2, counts.week)
        assertEquals(buckets.weekDays.sum(), counts.week)
        assertEquals(buckets.monthDays.sum(), counts.month)
        assertEquals(buckets.yearMonths.sum(), counts.year)
        assertEquals(buckets.allTimeMonths.sum(), counts.allTime)
        assertEquals(buckets.weekDays.last(), counts.today)
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
            totalCorrect = 0f,
            totalAttempts = 0f,
            sortedProgressions = emptyList(),
            nowMillis = 1_750_000_000_000L
        )
        assertEquals(-1f, core.reviewAccuracy)
    }

    @Test
    fun buildStatsCore_allTimeIgnoresEntriesWithUnparseableTimestamp() {
        // A missing/unparseable burned_at means the assignment isn't actually burned yet — allTime
        // must reflect only the 2 successfully-parsed entries, not the raw item count of 3.
        val core = buildStatsCore(
            burnedTimestamps = listOf("2025-01-01T00:00:00.000Z", "not-a-timestamp", "2025-06-01T00:00:00.000Z"),
            learnedTimestamps = emptyList(),
            totalCorrect = 0f,
            totalAttempts = 0f,
            sortedProgressions = emptyList(),
            nowMillis = 1_750_000_000_000L
        )
        assertEquals(2, core.burned.allTime)
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
