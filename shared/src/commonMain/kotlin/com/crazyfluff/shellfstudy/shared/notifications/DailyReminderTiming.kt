package com.crazyfluff.shellfstudy.shared.notifications

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/**
 * Computes the next local wall-clock occurrence of a given hour/minute — used to self-reschedule
 * the daily study reminder. A plain periodic work request can't pin to a specific local hour or
 * survive DST correctly since its repeat interval is relative to enqueue time.
 */
object DailyReminderTiming {
    fun nextOccurrence(now: LocalDateTime, zone: TimeZone, hour: Int, minute: Int = 0): Instant {
        var next = now.date.atTime(hour, minute)
        if (next <= now) next = next.date.plus(1, DateTimeUnit.DAY).atTime(hour, minute)
        return next.toInstant(zone)
    }
}
