package com.crazyfluff.shellfstudy.core.data.model

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("h a", Locale.getDefault())

/**
 * "N due now · M more in the next 24h" / "Next up: N at 3 PM" / "All caught up" — shared by
 * [com.crazyfluff.shellfstudy.feature.dashboard.ReviewForecastCard]'s default summary and the
 * reviews-available notification body, so the two don't drift out of sync.
 */
fun reviewForecastSummary(forecast: ReviewForecast): String {
    val upcomingTotal = forecast.buckets.sumOf { it.newlyAvailableCount }
    return when {
        forecast.reviewsAvailableNow > 0 && upcomingTotal > 0 ->
            "${forecast.reviewsAvailableNow} due now · $upcomingTotal more in the next 24h"
        forecast.reviewsAvailableNow > 0 -> "${forecast.reviewsAvailableNow} due now"
        upcomingTotal > 0 -> {
            val next = forecast.buckets.first { it.newlyAvailableCount > 0 }
            val time = TIME_FORMATTER.format(next.availableAt.atZone(ZoneId.systemDefault()))
            "Next up: ${next.newlyAvailableCount} at $time"
        }
        else -> "All caught up — nothing due in the next 24h"
    }
}
