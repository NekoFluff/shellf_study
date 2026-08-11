package com.crazyfluff.shellfstudy.core.notifications

import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Pure quiet-hours math, handling the overnight-wraparound case (e.g. 22:00-07:00) the same way
 * as the same-day case (e.g. 09:00-17:00 would never apply here, but the math doesn't care).
 */
object QuietHours {
    fun isQuietNow(now: LocalTime, start: LocalTime, end: LocalTime): Boolean {
        if (start == end) return false
        return if (start.isBefore(end)) {
            !now.isBefore(start) && now.isBefore(end)
        } else {
            !now.isBefore(start) || now.isBefore(end)
        }
    }

    /** The next instant quiet hours end, given [nowDateTime] is currently inside the quiet window. */
    fun nextEndInstant(nowDateTime: LocalDateTime, zone: ZoneId, start: LocalTime, end: LocalTime): Instant {
        val nowTime = nowDateTime.toLocalTime()
        var endDateTime = nowDateTime.toLocalDate().atTime(end)
        // Overnight window (start > end): "now" is either still in yesterday's window's tail
        // (before end, same day) or in the start of tonight's window (after start, ends tomorrow).
        if (start.isAfter(end) && !nowTime.isBefore(start)) {
            endDateTime = endDateTime.plusDays(1)
        }
        if (!endDateTime.isAfter(nowDateTime)) {
            endDateTime = endDateTime.plusDays(1)
        }
        return endDateTime.atZone(zone).toInstant()
    }
}
