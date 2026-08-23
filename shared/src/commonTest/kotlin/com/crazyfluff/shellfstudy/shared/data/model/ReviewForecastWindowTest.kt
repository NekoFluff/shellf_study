package com.crazyfluff.shellfstudy.shared.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReviewForecastWindowTest {

    @Test
    fun bucketHoursDividesTotalHoursEvenly() {
        ReviewForecastWindow.entries.forEach { window ->
            assertEquals(
                0, window.totalHours % window.bucketHours,
                "${window.name}: bucketHours (${window.bucketHours}) must divide totalHours (${window.totalHours}) evenly"
            )
        }
    }

    @Test
    fun fourMonths_isTheLongestWindow() {
        val longest = ReviewForecastWindow.entries.maxBy { it.totalHours }
        assertEquals(ReviewForecastWindow.FOUR_MONTHS, longest)
    }

    @Test
    fun fourMonths_isCappedAtFourThirtyDayMonths_notAFullYear() {
        assertEquals(24 * 120, ReviewForecastWindow.FOUR_MONTHS.totalHours)
        assertTrue(ReviewForecastWindow.FOUR_MONTHS.totalHours < 24 * 365)
    }

    @Test
    fun everyWindowProducesAReasonableBarCount() {
        // Too few bars looks sparse, too many stop being tappable/legible on a small chart.
        ReviewForecastWindow.entries.forEach { window ->
            assertTrue(
                window.bucketCount in 4..30,
                "${window.name}: bucketCount (${window.bucketCount}) should be readable on a small chart"
            )
        }
    }
}
