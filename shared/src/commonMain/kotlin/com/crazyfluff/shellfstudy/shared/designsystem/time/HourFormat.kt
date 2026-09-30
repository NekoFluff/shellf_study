package com.crazyfluff.shellfstudy.shared.designsystem.time

import androidx.compose.runtime.Composable

/** Whether the device is set to a 24-hour clock — Android's "Use 24-hour format", iOS's
 *  "24-Hour Time" — so hour-only settings (the daily reminder, quiet hours) read the way every
 *  other time on the device does. */
@Composable
expect fun rememberIs24HourClock(): Boolean

/** An on-the-hour label: "20:00" on a 24-hour clock, "8:00 PM" otherwise. [hour] wraps, so a
 *  caller stepping past midnight in either direction still gets a valid label. */
fun formatHour(hour: Int, is24h: Boolean): String {
    val normalized = hour.mod(24)
    if (is24h) return "${normalized.toString().padStart(2, '0')}:00"
    val hour12 = if (normalized % 12 == 0) 12 else normalized % 12
    val suffix = if (normalized < 12) "AM" else "PM"
    return "$hour12:00 $suffix"
}

/** The hour picker grid's cell label, where a full label won't fit in 24 cells. On a 24-hour
 *  clock that's "20". On a 12-hour clock it's the bare "8", because the grid's AM/PM halves carry
 *  the suffix. */
fun formatHourShort(hour: Int, is24h: Boolean): String {
    val normalized = hour.mod(24)
    if (is24h) return normalized.toString().padStart(2, '0')
    return (if (normalized % 12 == 0) 12 else normalized % 12).toString()
}
