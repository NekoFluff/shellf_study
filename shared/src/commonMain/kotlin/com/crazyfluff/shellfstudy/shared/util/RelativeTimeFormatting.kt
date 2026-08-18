package com.crazyfluff.shellfstudy.shared.util

import kotlin.time.Clock

/** "3 minutes ago" / "2 hours ago" / "5 days ago" style relative timestamp, coarse enough that it
 *  doesn't need to be continuously re-computed while on screen. */
fun formatRelativeTime(millis: Long?): String {
    if (millis == null) return "an earlier time"
    val minutesAgo = (Clock.System.now().toEpochMilliseconds() - millis).coerceAtLeast(0) / 60_000
    return when {
        minutesAgo < 1 -> "just now"
        minutesAgo < 60 -> "$minutesAgo minute${if (minutesAgo == 1L) "" else "s"} ago"
        minutesAgo < 60 * 24 -> {
            val hoursAgo = minutesAgo / 60
            "$hoursAgo hour${if (hoursAgo == 1L) "" else "s"} ago"
        }
        else -> {
            val daysAgo = minutesAgo / (60 * 24)
            "$daysAgo day${if (daysAgo == 1L) "" else "s"} ago"
        }
    }
}
