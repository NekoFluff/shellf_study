package com.crazyfluff.shellfstudy.shared.util

import kotlin.test.Test
import kotlin.test.assertEquals

class RelativeTimeFormattingTest {
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour
    private val start = 1_000_000_000_000L

    @Test
    fun formatsEachRange() {
        assertEquals("just now", formatRelativeTime(start, start + 59_999))
        assertEquals("1 minute ago", formatRelativeTime(start, start + minute))
        assertEquals("59 minutes ago", formatRelativeTime(start, start + hour - 1))
        assertEquals("1 hour ago", formatRelativeTime(start, start + hour))
        assertEquals("23 hours ago", formatRelativeTime(start, start + day - 1))
        assertEquals("1 day ago", formatRelativeTime(start, start + day))
        assertEquals("3 days ago", formatRelativeTime(start, start + 3 * day))
    }

    @Test
    fun aFutureTimestampReadsAsJustNow_andAMissingOneAsEarlier() {
        assertEquals("just now", formatRelativeTime(start + 5 * minute, start))
        assertEquals("an earlier time", formatRelativeTime(null, start))
    }

    @Test
    fun theNextChangeIsTheNextMinuteMarkInTheFirstHour() {
        assertEquals(minute, msUntilRelativeTimeChanges(start, start))
        assertEquals(30_000, msUntilRelativeTimeChanges(start, start + 4 * minute + 30_000))
        assertEquals(1, msUntilRelativeTimeChanges(start, start + 2 * minute - 1))
    }

    @Test
    fun theNextChangeIsTheNextHourMarkInTheFirstDay_thenTheNextDayMark() {
        assertEquals(hour, msUntilRelativeTimeChanges(start, start + hour))
        assertEquals(10 * minute, msUntilRelativeTimeChanges(start, start + 2 * hour + 50 * minute))
        assertEquals(day, msUntilRelativeTimeChanges(start, start + day))
        assertEquals(6 * hour, msUntilRelativeTimeChanges(start, start + 2 * day + 18 * hour))
    }

    @Test
    fun aFutureTimestampChangesOnceItIsAMinuteOld() {
        assertEquals(6 * minute, msUntilRelativeTimeChanges(start + 5 * minute, start))
    }

    @Test
    fun theTextChangesExactlyWhenTheDelaySaysItWill() {
        var now = start + 37_123
        repeat(200) {
            val before = formatRelativeTime(start, now)
            val wait = msUntilRelativeTimeChanges(start, now)
            assertEquals(before, formatRelativeTime(start, now + wait - 1))
            now += wait
            check(formatRelativeTime(start, now) != before) { "unchanged after waiting at $now" }
        }
    }
}
