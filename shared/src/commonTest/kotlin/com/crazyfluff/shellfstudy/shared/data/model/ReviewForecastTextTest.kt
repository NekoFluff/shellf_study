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
        assertEquals("5 due now · 2 more in the next 24h", reviewForecastSummary(forecast))
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
        assertTrue(reviewForecastSummary(forecast).startsWith("Next up: 3 at"))
    }

    @Test
    fun nothingDueNowOrUpcomingReportsFullyCaughtUp() {
        val forecast = ReviewForecast(reviewsAvailableNow = 0, buckets = (1..24).map { bucket(it, 0) })
        assertEquals("All caught up — nothing due in the next 24h", reviewForecastSummary(forecast))
    }
}
