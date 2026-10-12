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

/** "12 kanji left to Guru". */
internal fun kanjiLeftCaption(kanjiLeft: Int): String = "$kanjiLeft kanji left to Guru"

/** "Lessons ready now: 2 kanji · 1 radical", or null when the step has no lessons. Lessons are always
 *  ready now — an unlocked lesson can be done straight away. */
internal fun lessonsCaption(step: LevelUpStep): String? =
    countsPhrase(kanji = step.kanjiLessons, radicals = step.radicalLessons)?.let { "Lessons ready now: $it" }

/** "Reviews ready now: 3 kanji · 2 radicals", or with a time ("Next reviews: Today 2:00 PM · 1 radical")
 *  for a session that isn't available yet. Null when the step has no reviews. */
internal fun reviewsCaption(step: LevelUpStep, now: Instant, is24h: Boolean, zone: TimeZone): String? {
    val counts = countsPhrase(kanji = step.kanjiReviews, radicals = step.radicalReviews) ?: return null
    return if (step.at <= now) {
        "Reviews ready now: $counts"
    } else {
        "Next reviews: ${dayAndHour(step.at, now, is24h, zone)} · $counts"
    }
}

/** "3 kanji · 2 radicals" — kanji first, empty parts left out; null when both are zero. */
private fun countsPhrase(kanji: Int, radicals: Int): String? = listOfNotNull(
    "$kanji kanji".takeIf { kanji > 0 },
    "$radicals ${if (radicals == 1) "radical" else "radicals"}".takeIf { radicals > 0 }
).joinToString(" · ").ifEmpty { null }

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
