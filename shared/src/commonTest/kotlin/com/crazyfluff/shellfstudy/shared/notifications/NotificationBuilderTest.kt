package com.crazyfluff.shellfstudy.shared.notifications

import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecast
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecastBucket
import com.crazyfluff.shellfstudy.shared.data.model.reviewForecastSummary
import com.crazyfluff.shellfstudy.shared.notifications.NotificationBuilder
import com.crazyfluff.shellfstudy.shared.notifications.NotificationChannels
import com.crazyfluff.shellfstudy.shared.notifications.NotificationDeepLink
import com.crazyfluff.shellfstudy.shared.notifications.NotificationIds
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.time.Clock

class NotificationBuilderTest {

    @Test
    fun `reviewsAvailable reuses the shared forecast summary as its body`() {
        val forecast = ReviewForecast(
            reviewsAvailableNow = 4,
            buckets = listOf(
                ReviewForecastBucket(hoursFromNow = 1, availableAt = Clock.System.now(), newlyAvailableCount = 0)
            )
        )
        val spec = NotificationBuilder.reviewsAvailable(forecast = forecast)

        assertEquals(NotificationIds.REVIEWS_AVAILABLE, spec.id)
        assertEquals(NotificationChannels.REVIEWS_AVAILABLE, spec.channelId)
        assertEquals("Reviews are ready for you", spec.title)
        assertEquals(reviewForecastSummary(forecast), spec.body)
        assertEquals(NotificationDeepLink.DESTINATION_DASHBOARD, spec.destination)
    }

    @Test
    fun `reviewsAvailable title stays constant regardless of the due-now count`() {
        val forecast = ReviewForecast(reviewsAvailableNow = 72, buckets = emptyList())
        val spec = NotificationBuilder.reviewsAvailable(forecast = forecast)
        assertEquals("Reviews are ready for you", spec.title)
        assertEquals(reviewForecastSummary(forecast), spec.body)
    }

    @Test
    fun `reviewsBacklog targets the backlog channel and dashboard destination`() {
        val spec = NotificationBuilder.reviewsBacklog(totalDueNow = 75)
        assertEquals(NotificationIds.REVIEWS_BACKLOG, spec.id)
        assertEquals(NotificationChannels.REVIEWS_BACKLOG, spec.channelId)
        assertEquals(NotificationDeepLink.DESTINATION_DASHBOARD, spec.destination)
        assertTrue("75" in spec.body)
    }

    @Test
    fun `studyReminder mentions the current streak when active`() {
        val spec = NotificationBuilder.studyReminder(currentStreakDays = 12)
        assertEquals(NotificationChannels.STUDY_REMINDER, spec.channelId)
        assertEquals(NotificationDeepLink.DESTINATION_DASHBOARD, spec.destination)
        assertEquals("Keep your streak going", spec.title)
        assertTrue("12-day streak" in spec.body)
    }

    @Test
    fun `studyReminder without a streak still nudges the user`() {
        val spec = NotificationBuilder.studyReminder(currentStreakDays = 0)
        assertEquals("Ready to study?", spec.title)
        assertEquals("A quick session today gets your streak started.", spec.body)
    }

}
