package com.crazyfluff.shellfstudy.core.notifications

import java.time.Instant
import java.time.ZonedDateTime

/**
 * Computes the next local wall-clock occurrence of a given hour/minute — used to self-reschedule
 * the daily study reminder. A plain [androidx.work.PeriodicWorkRequest] can't pin to a specific
 * local hour or survive DST correctly since its repeat interval is relative to enqueue time.
 */
object DailyReminderTiming {
    fun nextOccurrence(now: ZonedDateTime, hour: Int, minute: Int = 0): Instant {
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.toInstant()
    }
}
