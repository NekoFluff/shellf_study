package com.crazyfluff.shellfstudy.shared.data.model

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * "N due now · M more in the next 24h" / "Next up: N at 3 PM" / "All caught up" — shared by
 * [com.crazyfluff.shellfstudy.shared.feature.dashboard.ReviewForecastCard]'s default summary and the
 * reviews-available notification body, so the two don't drift out of sync. [windowLabel] reflects
 * [forecast]'s actual bucket span (e.g. "48h") — the notification always forecasts the default
 * [ReviewForecastWindow.DAY], so it relies on this parameter's default rather than passing one.
 */
fun reviewForecastSummary(forecast: ReviewForecast, windowLabel: String = ReviewForecastWindow.DAY.label): String {
    val upcomingTotal = forecast.buckets.sumOf { it.newlyAvailableCount }
    return when {
        forecast.reviewsAvailableNow > 0 && upcomingTotal > 0 ->
            "${forecast.reviewsAvailableNow} due now · $upcomingTotal more in $windowLabel"
        forecast.reviewsAvailableNow > 0 -> "${forecast.reviewsAvailableNow} due now"
        upcomingTotal > 0 -> {
            val next = forecast.buckets.first { it.newlyAvailableCount > 0 }
            val bucketHours = forecast.buckets.first().hoursFromNow
            "Next: ${next.newlyAvailableCount} ${bucketMomentPhrase(next.availableAt, bucketHours)}"
        }
        else -> "All caught up"
    }
}

/** 12-hour "3 PM"-style formatting of [instant] in the device's local time zone (no minutes —
 *  matches the original `DateTimeFormatter.ofPattern("h a")` behavior this replaces). */
fun formatHourOfDay(instant: Instant): String {
    val hour24 = instant.toLocalDateTime(TimeZone.currentSystemDefault()).hour
    val amPm = if (hour24 < 12) "AM" else "PM"
    val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
    return "$hour12 $amPm"
}

/** "3/15" — a bucket wider than an hour rolls many different hours-of-day together (see
 *  [com.crazyfluff.shellfstudy.shared.data.AssignmentRepository.observeReviewForecast]'s "now"-
 *  rolling bucketing), so [formatHourOfDay] alone can't identify which one it is; a short date can. */
fun formatBucketDate(instant: Instant): String {
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.monthNumber}/${local.dayOfMonth}"
}

/** "at 3 PM" for an hourly bucket, "by 3/15" for anything wider — [bucketHours] is a bucket's own
 *  span (a [ReviewForecastBucket.hoursFromNow] taken from the first bucket, since bucket 1's
 *  cumulative offset from now equals its span). Shared so the summary sentence and the chart's
 *  tap-to-inspect detail describe the same moment the same way. */
fun bucketMomentPhrase(instant: Instant, bucketHours: Int): String =
    if (bucketHours <= 1) "at ${formatHourOfDay(instant)}" else "by ${formatBucketDate(instant)}"
