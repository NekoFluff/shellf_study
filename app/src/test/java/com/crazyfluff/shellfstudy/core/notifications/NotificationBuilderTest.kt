package com.crazyfluff.shellfstudy.core.notifications

import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecast
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecastBucket
import com.crazyfluff.shellfstudy.shared.data.model.reviewForecastSummary
import com.crazyfluff.shellfstudy.shared.notifications.NotificationBuilder
import com.crazyfluff.shellfstudy.shared.notifications.NotificationChannels
import com.crazyfluff.shellfstudy.shared.notifications.NotificationDeepLink
import com.crazyfluff.shellfstudy.shared.notifications.NotificationIds
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.time.Clock

class NotificationBuilderTest {

    @Test
    fun `reviewsAvailable reuses the shared forecast summary as its body`() {
        val forecast = ReviewForecast(
            reviewsAvailableNow = 4,
            buckets = listOf(ReviewForecastBucket(hoursFromNow = 1, availableAt = Clock.System.now(), newlyAvailableCount = 0))
        )
        val spec = NotificationBuilder.reviewsAvailable(forecast = forecast)

        assertThat(spec.id).isEqualTo(NotificationIds.REVIEWS_AVAILABLE)
        assertThat(spec.channelId).isEqualTo(NotificationChannels.REVIEWS_AVAILABLE)
        assertThat(spec.title).isEqualTo("Reviews are ready for you")
        assertThat(spec.body).isEqualTo(reviewForecastSummary(forecast))
        assertThat(spec.destination).isEqualTo(NotificationDeepLink.DESTINATION_DASHBOARD)
    }

    @Test
    fun `reviewsAvailable title stays constant regardless of the due-now count`() {
        val forecast = ReviewForecast(reviewsAvailableNow = 72, buckets = emptyList())
        val spec = NotificationBuilder.reviewsAvailable(forecast = forecast)
        assertThat(spec.title).isEqualTo("Reviews are ready for you")
        assertThat(spec.body).isEqualTo(reviewForecastSummary(forecast))
    }

    @Test
    fun `reviewsBacklog targets the backlog channel and dashboard destination`() {
        val spec = NotificationBuilder.reviewsBacklog(totalDueNow = 75, threshold = 50)
        assertThat(spec.id).isEqualTo(NotificationIds.REVIEWS_BACKLOG)
        assertThat(spec.channelId).isEqualTo(NotificationChannels.REVIEWS_BACKLOG)
        assertThat(spec.destination).isEqualTo(NotificationDeepLink.DESTINATION_DASHBOARD)
        assertThat(spec.body).contains("75")
    }

    @Test
    fun `studyReminder mentions the current streak when active`() {
        val spec = NotificationBuilder.studyReminder(currentStreakDays = 12)
        assertThat(spec.channelId).isEqualTo(NotificationChannels.STUDY_REMINDER)
        assertThat(spec.destination).isEqualTo(NotificationDeepLink.DESTINATION_DASHBOARD)
        assertThat(spec.title).isEqualTo("Keep your streak going")
        assertThat(spec.body).contains("12-day streak")
    }

    @Test
    fun `studyReminder without a streak still nudges the user`() {
        val spec = NotificationBuilder.studyReminder(currentStreakDays = 0)
        assertThat(spec.title).isEqualTo("Ready to study?")
        assertThat(spec.body).isEqualTo("A quick session today gets your streak started.")
    }

}
