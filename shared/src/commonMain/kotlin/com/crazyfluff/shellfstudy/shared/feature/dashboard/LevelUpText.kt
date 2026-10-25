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

/** Within this many days a date reads as a weekday ("Thu"); further out it needs the month and day. */
private const val WEEKDAY_HORIZON_DAYS = 6

/** "Fastest level-up · Thu 3:00 PM (in 2d 6h)". */
internal fun levelUpEtaCaption(levelUpAt: Instant, now: Instant, is24h: Boolean, zone: TimeZone): String =
    "Fastest level-up · ${dayAndHour(levelUpAt, now, is24h, zone)} (${relativeEta(levelUpAt, now, zone)})"

/** "Unlocks · Mar 14, 2027 9:00 AM (in 1y 4mo)" — when a level the user hasn't reached opens up. */
internal fun unlockEtaCaption(unlockAt: Instant, now: Instant, is24h: Boolean, zone: TimeZone): String =
    "Unlocks · ${dayAndHour(unlockAt, now, is24h, zone)} (${relativeEta(unlockAt, now, zone)})"

/** "Level takes 6d 21h" — from a level unlocking to its fastest level-up. */
internal fun levelDurationCaption(unlockAt: Instant, levelUpAt: Instant, zone: TimeZone): String =
    "Level takes ${durationText(unlockAt, levelUpAt, zone)}"

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

/** "in 2d 6h", "in 5h", "in under 1h", "in 1y 4mo" — see [durationText]. */
internal fun relativeEta(target: Instant, now: Instant, zone: TimeZone): String = "in ${durationText(now, target, zone)}"

/** "Today 3:00 PM", "Tomorrow 3:00 PM", "Thu 3:00 PM" within the coming week; further out "Mar 14
 *  3:00 PM", with the year added ("Mar 14, 2027 3:00 PM") once it isn't this one. A weekday name
 *  for today would read as next week, hence the two special cases. */
private fun dayAndHour(at: Instant, now: Instant, is24h: Boolean, zone: TimeZone): String {
    val local = at.toLocalDateTime(zone)
    val today: LocalDate = now.toLocalDateTime(zone).date
    val date = local.date
    val day = when {
        date == today -> "Today"
        date == today.plus(DatePeriod(days = 1)) -> "Tomorrow"
        date <= today.plus(DatePeriod(days = WEEKDAY_HORIZON_DAYS)) -> shortDayName(date.dayOfWeek)
        date.year == today.year -> "${shortMonthName(date.month)} ${date.day}"
        else -> "${shortMonthName(date.month)} ${date.day}, ${date.year}"
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
