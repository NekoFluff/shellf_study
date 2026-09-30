package com.crazyfluff.shellfstudy.shared.designsystem.time

import androidx.compose.runtime.Composable

const val HOURS_PER_DAY = 24
private const val HOURS_PER_HALF_DAY = 12

/** Whether the device is set to a 24-hour clock — Android's "Use 24-hour format", iOS's
 *  "24-Hour Time" — so hour-only settings (the daily reminder, quiet hours) read the way every
 *  other time on the device does. */
@Composable
expect fun rememberIs24HourClock(): Boolean

/** An on-the-hour label: "20:00" on a 24-hour clock, "8:00 PM" otherwise. [hour] wraps, so a
 *  caller stepping past midnight in either direction still gets a valid label. */
fun formatHour(hour: Int, is24h: Boolean): String {
    val normalized = hour.mod(HOURS_PER_DAY)
    if (is24h) return "${twoDigits(normalized)}:00"
    val suffix = if (normalized < HOURS_PER_HALF_DAY) "AM" else "PM"
    return "${to12Hour(normalized)}:00 $suffix"
}

/** The hour picker grid's cell label, where a full label won't fit in 24 cells. On a 24-hour
 *  clock that's "20". On a 12-hour clock it's the bare "8", because the grid's AM/PM halves carry
 *  the suffix. */
fun formatHourShort(hour: Int, is24h: Boolean): String {
    val normalized = hour.mod(HOURS_PER_DAY)
    return if (is24h) twoDigits(normalized) else to12Hour(normalized).toString()
}

/** 0–23 to 1–12: midnight and noon are both 12. */
private fun to12Hour(hour: Int): Int = (hour % HOURS_PER_HALF_DAY).takeIf { it != 0 } ?: HOURS_PER_HALF_DAY

private fun twoDigits(hour: Int): String = hour.toString().padStart(2, '0')
