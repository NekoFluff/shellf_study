package com.crazyfluff.shellfstudy.shared.feature.dashboard

import com.crazyfluff.shellfstudy.shared.data.model.LevelUpStep
import com.crazyfluff.shellfstudy.shared.designsystem.time.formatHour
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private const val HOUR_MS = 3_600_000L
private const val HOURS_PER_DAY = 24

/** "Fastest level-up · Thu 3:00 PM (in 2d 6h)". */
internal fun levelUpEtaCaption(levelUpAt: Instant, now: Instant, is24h: Boolean, zone: TimeZone): String =
    "Fastest level-up · ${dayAndHour(levelUpAt, now, is24h, zone)} (${relativeEta(levelUpAt, now)})"

/**
 * "Next: Today 2:00 PM · 4 radical reviews", "Next: now · 3 kanji lessons", or, for a session mixing
 * subject types or lessons with reviews, "Next: now · 2 radicals, 3 kanji".
 */
internal fun nextStepCaption(step: LevelUpStep, now: Instant, is24h: Boolean, zone: TimeZone): String {
    val whenLabel = if (step.at <= now) "now" else dayAndHour(step.at, now, is24h, zone)
    return "Next: $whenLabel · ${step.itemsPhrase}"
}

/** "in 2d 6h", "in 5h", "in under 1h" — whole hours, rounded down. */
internal fun relativeEta(target: Instant, now: Instant): String {
    val hours = ((target - now).inWholeMilliseconds / HOUR_MS).coerceAtLeast(0)
    val days = hours / HOURS_PER_DAY
    val remainder = hours % HOURS_PER_DAY
    return when {
        hours == 0L -> "in under 1h"
        days == 0L -> "in ${remainder}h"
        remainder == 0L -> "in ${days}d"
        else -> "in ${days}d ${remainder}h"
    }
}

/** "Today 3:00 PM", "Tomorrow 3:00 PM", "Thu 3:00 PM". A weekday name for today would read as next
 *  week, hence the two special cases. */
private fun dayAndHour(at: Instant, now: Instant, is24h: Boolean, zone: TimeZone): String {
    val local = at.toLocalDateTime(zone)
    val today: LocalDate = now.toLocalDateTime(zone).date
    val day = when (local.date) {
        today -> "Today"
        today.plus(DatePeriod(days = 1)) -> "Tomorrow"
        else -> shortDayName(local.date.dayOfWeek)
    }
    return "$day ${formatHour(local.hour, is24h)}"
}

private fun shortDayName(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "Mon"
    DayOfWeek.TUESDAY -> "Tue"
    DayOfWeek.WEDNESDAY -> "Wed"
    DayOfWeek.THURSDAY -> "Thu"
    DayOfWeek.FRIDAY -> "Fri"
    DayOfWeek.SATURDAY -> "Sat"
    DayOfWeek.SUNDAY -> "Sun"
}
