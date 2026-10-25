package com.crazyfluff.shellfstudy.shared.feature.dashboard

import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.periodUntil
import kotlin.time.Instant

private const val HOUR_MS = 3_600_000L
private const val HOURS_PER_DAY = 24
private const val DAYS_PER_WEEK = 7

/** Up to here a span reads in days and hours ("13d 23h"); past it, weeks, months or years. */
private const val MAX_DAY_HOUR_SPAN_HOURS = 14L * HOURS_PER_DAY

/** Below this many calendar months a span reads in weeks and days ("8w 3d"). */
private const val MIN_MONTH_SPAN = 2

/**
 * The span from [from] to [to], rounded down, in the two largest units that read naturally at its
 * size: "under 1h", "5h", "13d 23h", "8w 3d", "4mo 12d", "1y 4mo". A zero second unit is dropped
 * ("2d", "3w", "2y"), and a negative span counts as "under 1h".
 *
 * Months and years are calendar ones in [zone], so "1y" is the same date next year rather than 365
 * days on.
 */
internal fun durationText(from: Instant, to: Instant, zone: TimeZone): String {
    val hours = ((to - from).inWholeMilliseconds / HOUR_MS).coerceAtLeast(0)
    val days = hours / HOURS_PER_DAY
    val period by lazy { from.periodUntil(to, zone) }
    return when {
        hours == 0L -> "under 1h"
        hours < HOURS_PER_DAY -> "${hours}h"
        hours < MAX_DAY_HOUR_SPAN_HOURS -> twoUnits(days, "d", hours % HOURS_PER_DAY, "h")
        period.years > 0 -> twoUnits(period.years.toLong(), "y", period.months.toLong(), "mo")
        period.months >= MIN_MONTH_SPAN -> twoUnits(period.months.toLong(), "mo", period.days.toLong(), "d")
        else -> twoUnits(days / DAYS_PER_WEEK, "w", days % DAYS_PER_WEEK, "d")
    }
}

private fun twoUnits(major: Long, majorUnit: String, minor: Long, minorUnit: String): String =
    if (minor == 0L) "$major$majorUnit" else "$major$majorUnit $minor$minorUnit"

internal fun shortMonthName(month: Month): String = when (month) {
    Month.JANUARY -> "Jan"
    Month.FEBRUARY -> "Feb"
    Month.MARCH -> "Mar"
    Month.APRIL -> "Apr"
    Month.MAY -> "May"
    Month.JUNE -> "Jun"
    Month.JULY -> "Jul"
    Month.AUGUST -> "Aug"
    Month.SEPTEMBER -> "Sep"
    Month.OCTOBER -> "Oct"
    Month.NOVEMBER -> "Nov"
    Month.DECEMBER -> "Dec"
}
