package com.crazyfluff.shellfstudy.shared.data

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Tests for [durationUntilNextMidnight] — the piece of [dailyRolloverTicks] that can be tested
 * deterministically, since it's a pure function of `now` rather than reading `Clock.System` itself.
 * [dailyRolloverTicks] backs the day-boundary rollover in [AssignmentRepository.observeLessonsCompletedToday],
 * [StatsRepository.observeStudyStreak], and [FriendStatsRepository]'s self leaderboard stats.
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
}
