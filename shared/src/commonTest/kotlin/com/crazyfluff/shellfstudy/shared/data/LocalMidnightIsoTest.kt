package com.crazyfluff.shellfstudy.shared.data

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [localMidnightIso] feeds a *lexicographic* SQL comparison against the `startedAt` values WaniKani
 * writes, so both its value and its exact output format are load-bearing.
 *
 * These tests exist in their current form because the first version of this function was wrong in a
 * way the tests did not catch. It stamped the date onto a literal `T00:00:00Z` — a *UTC* midnight —
 * and every test here asserted that literal string, so they agreed with the bug. On a device at
 * UTC-7 the cutoff was seven hours too early, and "lessons completed today" reported 13 on a day the
 * user had completed none: every assignment started between 17:00 and midnight the previous local
 * day was inside the window.
 *
 * The lesson is that a timezone-sensitive helper needs its expected value derived from the
 * timezone, not restated as a constant. These cases name a zone explicitly and assert the instant
 * that zone's midnight actually falls at.
 */
class LocalMidnightIsoTest {

    private val utc = TimeZone.UTC

    /** Phoenix is UTC-7 year-round (no DST), so its local midnight is 07:00 UTC. */
    private val phoenix = TimeZone.of("America/Phoenix")

    /**
     * The regression, stated directly: local midnight in a zone behind UTC must not be rendered as
     * midnight UTC. This is the exact case that produced the reported "13 lessons" false count.
     */
    @Test
    fun `uses the zone's offset not a UTC midnight`() {
        assertEquals("2026-09-28T07:00:00.000000Z", localMidnightIso(LocalDate(2026, 9, 28), phoenix))
    }

    @Test
    fun `UTC midnight is midnight UTC`() {
        assertEquals("2026-09-28T00:00:00.000000Z", localMidnightIso(LocalDate(2026, 9, 28), utc))
    }

    /** Zones ahead of UTC push local midnight into the previous UTC day. */
    @Test
    fun `a zone ahead of UTC lands on the previous UTC date`() {
        val tokyo = TimeZone.of("Asia/Tokyo") // UTC+9
        assertEquals("2026-09-27T15:00:00.000000Z", localMidnightIso(LocalDate(2026, 9, 28), tokyo))
    }

    /**
     * The offset must be resolved *for that date*, not once for the zone — otherwise a date either
     * side of a DST transition lands an hour out. New York is UTC-4 in summer and UTC-5 in winter.
     */
    @Test
    fun `resolves the offset for the given date across a DST boundary`() {
        val newYork = TimeZone.of("America/New_York")
        assertEquals("2026-07-01T04:00:00.000000Z", localMidnightIso(LocalDate(2026, 7, 1), newYork)) // EDT, UTC-4
        assertEquals("2026-12-01T05:00:00.000000Z", localMidnightIso(LocalDate(2026, 12, 1), newYork)) // EST, UTC-5
    }

    /** Zero-padded fields, so the digits — not punctuation — decide the comparison. */
    @Test
    fun `zero-pads single-digit month and day`() {
        assertEquals("2026-01-05T00:00:00.000000Z", localMidnightIso(LocalDate(2026, 1, 5), utc))
    }

    /** Fixed width, so a lexicographic comparison never sees a short string. */
    @Test
    fun `is always the same length regardless of date or zone`() {
        val lengths = listOf(
            LocalDate(2026, 1, 1) to utc,
            LocalDate(2026, 12, 31) to utc,
            LocalDate(2026, 6, 15) to phoenix,
            LocalDate(2026, 6, 15) to TimeZone.of("Asia/Tokyo")
        ).map { (date, zone) -> localMidnightIso(date, zone).length }.distinct()
        assertEquals(listOf(27), lengths)
    }

    /** WaniKani always writes an explicit six-digit fraction; the cutoff must match that shape. */
    @Test
    fun `always carries a six-digit zero fraction`() {
        val cutoff = localMidnightIso(LocalDate(2026, 9, 28), phoenix)
        assertTrue(cutoff.endsWith(".000000Z"), "expected a six-digit fraction, got $cutoff")
    }

    /**
     * The comparison the DAO performs, replayed in Kotlin against real WaniKani-shaped timestamps:
     * everything from local midnight onwards is counted, and the last instant of the previous local
     * day is not.
     *
     * This is the property the reported bug violated — 17:00-23:59 the previous local day sorted at
     * or above the cutoff and was counted as today.
     */
    @Test
    fun `counts from local midnight and excludes the previous local day`() {
        val cutoff = localMidnightIso(LocalDate(2026, 9, 28), phoenix) // 2026-09-28T07:00:00.000000Z

        val exactlyLocalMidnight = "2026-09-28T07:00:00.000000Z"
        val laterSameLocalDay = "2026-09-28T23:59:59.999999Z"
        // 23:59 local on the 27th is 06:59 UTC on the 28th — earlier than the cutoff, despite
        // sharing the 28th as its UTC date. This is the value the buggy cutoff wrongly included.
        val lastInstantOfPreviousLocalDay = "2026-09-28T06:59:59.999999Z"

        assertTrue(exactlyLocalMidnight >= cutoff, "local midnight itself belongs to today")
        assertTrue(laterSameLocalDay >= cutoff, "later the same local day must be counted")
        assertTrue(lastInstantOfPreviousLocalDay < cutoff, "the previous local day must not be counted")
    }
}
