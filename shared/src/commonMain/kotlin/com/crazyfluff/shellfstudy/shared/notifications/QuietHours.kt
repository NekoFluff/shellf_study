package com.crazyfluff.shellfstudy.shared.notifications

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/**
 * Pure quiet-hours math, handling the overnight-wraparound case (e.g. 22:00-07:00) the same way
 * as the same-day case (e.g. 09:00-17:00 would never apply here, but the math doesn't care).
 * Boundaries and "now" are hour-of-day only (no minutes) since quiet hours are only ever
 * configured to a whole hour ([com.crazyfluff.shellfstudy.shared.data.NotificationSettings]
 * stores plain hour Ints) — sub-hour precision on "now" can't change which side of an
 * hour-aligned boundary it falls on.
 */
object QuietHours {
    fun isQuietNow(nowHour: Int, startHour: Int, endHour: Int): Boolean {
        if (startHour == endHour) return false
        return if (startHour < endHour) {
            nowHour in startHour until endHour
        } else {
            nowHour >= startHour || nowHour < endHour
        }
    }

    /** The next instant quiet hours end, given [nowDateTime] is currently inside the quiet window. */
    fun nextEndInstant(nowDateTime: LocalDateTime, zone: TimeZone, startHour: Int, endHour: Int): Instant {
        var endDateTime = nowDateTime.date.atTime(endHour, 0)
        // Overnight window (start > end): "now" is either still in yesterday's window's tail
        // (before end, same day) or in the start of tonight's window (after start, ends tomorrow).
        if (startHour > endHour && nowDateTime.hour >= startHour) {
            endDateTime = endDateTime.date.plus(1, DateTimeUnit.DAY).atTime(endHour, 0)
        }
        if (endDateTime <= nowDateTime) {
            endDateTime = endDateTime.date.plus(1, DateTimeUnit.DAY).atTime(endHour, 0)
        }
        return endDateTime.toInstant(zone)
    }
}
