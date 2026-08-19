package com.crazyfluff.shellfstudy.shared.data

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Tests for computeActivityBuckets. All timestamps are at T12:00:00Z so results are unambiguous
 * across UTC-11 to UTC+11 (day/month assignment is stable regardless of local timezone).
 *
 * "now" is fixed at 2026-08-15T12:00:00Z throughout.
 */
class ActivityBucketCalculatorTest {

    private val tz = TimeZone.UTC
    // 2026-08-15 noon UTC — Aug 2026, totalMonths = 2026*12+7 = 24319
    private val nowMillis = Instant.parse("2026-08-15T12:00:00Z").toEpochMilliseconds()

    // ---------------------------------------------------------------------------
    // allTimeMonths — size
    // ---------------------------------------------------------------------------

    @Test
    fun emptyTimestamps_allTimeMonthsIsSingleCurrentMonthBucket() {
        val b = computeActivityBuckets(emptyList(), nowMillis, tz)
        assertEquals(listOf(0), b.allTimeMonths)
    }

    @Test
    fun allTimestampsInCurrentMonth_singleBucketWithCorrectCount() {
        val ts = listOf(
            "2026-08-01T12:00:00Z",
            "2026-08-10T12:00:00Z",
            "2026-08-15T11:00:00Z",
        )
        val b = computeActivityBuckets(ts, nowMillis, tz)
        assertEquals(listOf(3), b.allTimeMonths)
    }

    @Test
    fun timestampsSpanningThreeMonths_correctSizeAndCounts() {
        // Jun → Jul → Aug 2026 = 3 months
        val ts = listOf(
            "2026-06-15T12:00:00Z",  // 2 months ago, index 0
            "2026-07-15T12:00:00Z",  // 1 month ago, index 1
            "2026-08-10T12:00:00Z",  // current,     index 2
            "2026-08-12T12:00:00Z",  // current,     index 2
        )
        val b = computeActivityBuckets(ts, nowMillis, tz)
        assertEquals(3, b.allTimeMonths.size)
        assertEquals(listOf(1, 1, 2), b.allTimeMonths)
    }

    @Test
    fun timestampsSpanningMultipleYears_allBucketsPresent() {
        // Jan 2024 → Aug 2026: totalMonths 24288 → 24319 = 32 months
        val ts = listOf(
            "2024-01-15T12:00:00Z",  // index  0
            "2024-06-15T12:00:00Z",  // index  5
            "2025-03-15T12:00:00Z",  // index 14
            "2026-08-10T12:00:00Z",  // index 31
        )
        val b = computeActivityBuckets(ts, nowMillis, tz)
        assertEquals(32, b.allTimeMonths.size)
        assertEquals(1, b.allTimeMonths[0])
        assertEquals(1, b.allTimeMonths[5])
        assertEquals(1, b.allTimeMonths[14])
        assertEquals(1, b.allTimeMonths[31])
        assertEquals(4, b.allTimeMonths.sum())
    }

    @Test
    fun allTimeMonthsLastIndexAlwaysEqualsCurrentMonthCount() {
        val ts = listOf(
            "2025-01-15T12:00:00Z",
            "2026-08-10T12:00:00Z",
            "2026-08-14T12:00:00Z",
        )
        val b = computeActivityBuckets(ts, nowMillis, tz)
        // Last bucket = Aug 2026: 2 events
        assertEquals(2, b.allTimeMonths.last())
    }

    @Test
    fun multipleEventsInSameOldMonth_countedCorrectly() {
        val ts = listOf(
            "2025-05-01T12:00:00Z",
            "2025-05-15T12:00:00Z",
            "2025-05-31T12:00:00Z",
        )
        val b = computeActivityBuckets(ts, nowMillis, tz)
        // May 2025 = 2025*12+4 = 24304; Aug 2026 = 24319; diff = 15 months → size 16
        assertEquals(16, b.allTimeMonths.size)
        assertEquals(3, b.allTimeMonths[0])
        assertEquals(0, b.allTimeMonths.last())
    }

    // ---------------------------------------------------------------------------
    // yearMonths — regression: still correct after allTimeMonths added
    // ---------------------------------------------------------------------------

    @Test
    fun yearMonthsAlwaysHas12Elements() {
        val b = computeActivityBuckets(emptyList(), nowMillis, tz)
        assertEquals(12, b.yearMonths.size)
    }

    @Test
    fun yearMonthsExcludesEventsOlderThan12Months() {
        val ts = listOf(
            "2025-09-15T12:00:00Z",  // 11 months ago → yearMonths[0]
            "2026-08-10T12:00:00Z",  // current month → yearMonths[11]
            "2024-08-10T12:00:00Z",  // 24 months ago → NOT in yearMonths
        )
        val b = computeActivityBuckets(ts, nowMillis, tz)
        assertEquals(1, b.yearMonths[0])   // Sep 2025
        assertEquals(1, b.yearMonths[11])  // Aug 2026
        assertEquals(2, b.yearMonths.sum())
    }

    // ---------------------------------------------------------------------------
    // weekDays — regression
    // ---------------------------------------------------------------------------

    @Test
    fun weekDaysAlwaysHas7Elements() {
        val b = computeActivityBuckets(emptyList(), nowMillis, tz)
        assertEquals(7, b.weekDays.size)
    }

    @Test
    fun weekDays_eventsOutsideWindowExcluded() {
        // now = 2026-08-15; 6 days ago = Aug 9 (index 0); today = Aug 15 (index 6)
        val ts = listOf(
            "2026-08-09T12:00:00Z",  // 6 days ago → index 0
            "2026-08-15T11:00:00Z",  // today       → index 6
            "2026-08-15T10:00:00Z",  // today       → index 6
            "2026-08-08T12:00:00Z",  // 7 days ago  → excluded
        )
        val b = computeActivityBuckets(ts, nowMillis, tz)
        assertEquals(1, b.weekDays[0])  // Aug 9
        assertEquals(2, b.weekDays[6])  // Aug 15
        assertEquals(3, b.weekDays.sum())
    }

    // ---------------------------------------------------------------------------
    // computeWindowedCounts — today uses calendar midnight, not rolling 24h
    // ---------------------------------------------------------------------------

    @Test
    fun windowedCounts_todayUsesCalendarMidnightNotRolling24h() {
        // now = 2026-08-15T12:00:00Z
        // "yesterday at 23:00" is within 24h rolling but NOT calendar today
        val ts = listOf(
            "2026-08-14T23:00:00Z",  // 13h ago — rolling 24h would count this, calendar today should not
            "2026-08-15T01:00:00Z",  // today at 1am — calendar today
        )
        val counts = computeWindowedCounts(computeActivityBuckets(ts, nowMillis, tz))
        assertEquals(1, counts.today)   // only the Aug 15 event
        assertEquals(2, counts.week)    // both within 7 days
    }

    @Test
    fun windowedCounts_allTimeCountsEverything() {
        val ts = listOf(
            "2020-01-01T12:00:00Z",
            "2022-06-15T12:00:00Z",
            "2026-08-15T11:00:00Z",
        )
        val counts = computeWindowedCounts(computeActivityBuckets(ts, nowMillis, tz))
        assertEquals(3, counts.allTime)
    }
}
