package com.crazyfluff.shellfstudy.shared.data

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Tests for [durationUntilNextMidnight] and [durationUntilNextHour] — the pieces of
 * [dailyRolloverTicks] and [hourlyRolloverTicks] that can be tested deterministically, since they are
 * pure functions of `now` rather than readers of `Clock.System` themselves. [dailyRolloverTicks] backs
 * the day-boundary rollover in [AssignmentRepository.observeLessonsCompletedToday],
 * [StatsRepository.observeStudyStreak], and [FriendStatsRepository]'s self leaderboard stats;
 * [hourlyRolloverTicks] backs the hour-boundary re-subscription in
 * [AssignmentRepository.observeReviewForecast] and [AssignmentRepository.observeReviewDueCount].
 */
class DateRolloverTest {

    private val tz = TimeZone.UTC

    @Test
    fun justBeforeMidnight_returnsShortDuration() {
        val now = Instant.parse("2026-08-15T23:59:57Z")
        val result = durationUntilNextMidnight(now, tz)
        assertEquals(3.seconds, result)
    }

    @Test
    fun justAfterMidnight_returnsAlmostFullDay() {
        val now = Instant.parse("2026-08-15T00:00:01Z")
        val result = durationUntilNextMidnight(now, tz)
        assertEquals(24.hours - 1.seconds, result)
    }

    @Test
    fun exactlyAtMidnight_returnsFullDay() {
        val now = Instant.parse("2026-08-15T00:00:00Z")
        val result = durationUntilNextMidnight(now, tz)
        assertEquals(24.hours, result)
    }

    @Test
    fun justBeforeTheHour_returnsTheBufferOnly() {
        val now = Instant.parse("2026-08-15T13:59:59Z")
        assertEquals(6.seconds, durationUntilNextHour(now))
    }

    @Test
    fun exactlyOnTheHour_returnsAFullHourPlusTheBuffer() {
        val now = Instant.parse("2026-08-15T14:00:00Z")
        assertEquals(3605.seconds, durationUntilNextHour(now))
    }

    @Test
    fun justAfterTheHour_returnsAlmostAFullHourPlusTheBuffer() {
        val now = Instant.parse("2026-08-15T14:00:07Z")
        assertEquals(3598.seconds, durationUntilNextHour(now))
    }
}
