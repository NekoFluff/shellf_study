package com.crazyfluff.shellfstudy.shared.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class ReviewForecastTextTest {

    private fun bucket(hoursFromNow: Int, count: Int) = ReviewForecastBucket(
        hoursFromNow = hoursFromNow,
        availableAt = Instant.parse("2026-08-10T12:00:00Z") + hoursFromNow.hours,
        newlyAvailableCount = count
    )

    @Test
    fun combinesDueNowAndUpcomingCounts() {
        val forecast = ReviewForecast(
            reviewsAvailableNow = 5,
            buckets = (1..24).map { bucket(it, if (it == 3) 2 else 0) }
        )
        assertEquals("5 due now · 2 more in 24h", reviewForecastSummary(forecast))
    }

    @Test
    fun dueNowWithNothingUpcomingOmitsTheUpcomingClause() {
        val forecast = ReviewForecast(reviewsAvailableNow = 5, buckets = (1..24).map { bucket(it, 0) })
        assertEquals("5 due now", reviewForecastSummary(forecast))
    }

    @Test
    fun nothingDueNowButSomethingUpcomingNamesTheNextBatch() {
        val forecast = ReviewForecast(
            reviewsAvailableNow = 0,
            buckets = (1..24).map { bucket(it, if (it == 5) 3 else 0) }
        )
        assertTrue(reviewForecastSummary(forecast).startsWith("Next: 3 at"))
    }

    @Test
    fun nothingDueNowOrUpcomingReportsFullyCaughtUp() {
        val forecast = ReviewForecast(reviewsAvailableNow = 0, buckets = (1..24).map { bucket(it, 0) })
        assertEquals("All caught up", reviewForecastSummary(forecast))
    }

    @Test
    fun nothingDueNowButSomethingUpcoming_onAWiderThanHourlyWindow_namesTheNextBatchByDateAndTime() {
        // A week/month/4-month window's buckets span more than an hour each, so a bare "at 3 PM"
        // would be uninformative (or actively misleading — every bucket lands on the same rolling
        // hour-of-day). It should lead with the date instead — but a bucket is still a precise
        // hour, not a whole-day range, so the time stays too, just after the date.
        val forecast = ReviewForecast(
            reviewsAvailableNow = 0,
            buckets = (1..7).map { index -> bucket(hoursFromNow = index * 24, count = if (index == 2) 3 else 0) }
        )
        val summary = reviewForecastSummary(forecast)
        assertTrue(summary.startsWith("Next: 3 by"))
        assertTrue(summary.contains("AM") || summary.contains("PM"), "expected a time alongside the date: $summary")
    }

    @Test
    fun bucketMomentPhrase_usesAtForAnHourlyBucket_andByWithBothDateAndTimeForAnythingWider() {
        val instant = Instant.parse("2026-08-10T15:00:00Z")
        assertTrue(bucketMomentPhrase(instant, bucketHours = 1).startsWith("at "))

        val wide = bucketMomentPhrase(instant, bucketHours = 24)
        assertTrue(wide.startsWith("by "))
        assertTrue(wide.contains("AM") || wide.contains("PM"), "expected a time alongside the date: $wide")

        val wider = bucketMomentPhrase(instant, bucketHours = 240)
        assertTrue(wider.startsWith("by "))
        assertTrue(wider.contains("AM") || wider.contains("PM"), "expected a time alongside the date: $wider")
    }
}
