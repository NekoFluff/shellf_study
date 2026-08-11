package com.crazyfluff.shellfstudy.core.data.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class ReviewForecastTextTest {

    private fun bucket(hoursFromNow: Int, count: Int) = ReviewForecastBucket(
        hoursFromNow = hoursFromNow,
        availableAt = Instant.parse("2026-08-10T12:00:00Z").plusSeconds(hoursFromNow * 3600L),
        newlyAvailableCount = count
    )

    @Test
    fun `combines due-now and upcoming counts`() {
        val forecast = ReviewForecast(
            reviewsAvailableNow = 5,
            buckets = (1..24).map { bucket(it, if (it == 3) 2 else 0) }
        )
        assertThat(reviewForecastSummary(forecast)).isEqualTo("5 due now · 2 more in the next 24h")
    }

    @Test
    fun `due now with nothing upcoming omits the upcoming clause`() {
        val forecast = ReviewForecast(reviewsAvailableNow = 5, buckets = (1..24).map { bucket(it, 0) })
        assertThat(reviewForecastSummary(forecast)).isEqualTo("5 due now")
    }

    @Test
    fun `nothing due now but something upcoming names the next batch`() {
        val forecast = ReviewForecast(
            reviewsAvailableNow = 0,
            buckets = (1..24).map { bucket(it, if (it == 5) 3 else 0) }
        )
        assertThat(reviewForecastSummary(forecast)).startsWith("Next up: 3 at")
    }

    @Test
    fun `nothing due now or upcoming reports fully caught up`() {
        val forecast = ReviewForecast(reviewsAvailableNow = 0, buckets = (1..24).map { bucket(it, 0) })
        assertThat(reviewForecastSummary(forecast)).isEqualTo("All caught up — nothing due in the next 24h")
    }
}
