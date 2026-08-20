package com.crazyfluff.shellfstudy.shared.data.model

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * "N due now · M more in the next 24h" / "Next up: N at 3 PM" / "All caught up" — shared by
 * [com.crazyfluff.shellfstudy.shared.feature.dashboard.ReviewForecastCard]'s default summary and the
 * reviews-available notification body, so the two don't drift out of sync.
 */
fun reviewForecastSummary(forecast: ReviewForecast): String {
    val upcomingTotal = forecast.buckets.sumOf { it.newlyAvailableCount }
    return when {
        forecast.reviewsAvailableNow > 0 && upcomingTotal > 0 ->
            "${forecast.reviewsAvailableNow} due now · $upcomingTotal more in 24h"
        forecast.reviewsAvailableNow > 0 -> "${forecast.reviewsAvailableNow} due now"
        upcomingTotal > 0 -> {
            val next = forecast.buckets.first { it.newlyAvailableCount > 0 }
            "Next: ${next.newlyAvailableCount} at ${formatHourOfDay(next.availableAt)}"
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
