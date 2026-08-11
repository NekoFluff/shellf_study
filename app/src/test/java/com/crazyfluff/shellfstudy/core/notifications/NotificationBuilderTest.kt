package com.crazyfluff.shellfstudy.core.notifications

import com.crazyfluff.shellfstudy.core.data.model.ReviewForecast
import com.crazyfluff.shellfstudy.core.data.model.ReviewForecastBucket
import com.crazyfluff.shellfstudy.core.data.model.reviewForecastSummary
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class NotificationBuilderTest {

    @Test
    fun `reviewsAvailable reuses the shared forecast summary as its body`() {
        val forecast = ReviewForecast(
            reviewsAvailableNow = 4,
            buckets = listOf(ReviewForecastBucket(hoursFromNow = 1, availableAt = Instant.now(), newlyAvailableCount = 0))
        )
        val spec = NotificationBuilder.reviewsAvailable(newCount = 4, forecast = forecast)

        assertThat(spec.id).isEqualTo(NotificationIds.REVIEWS_AVAILABLE)
        assertThat(spec.channelId).isEqualTo(NotificationChannels.REVIEWS_AVAILABLE)
        assertThat(spec.title).isEqualTo("4 reviews are available")
        assertThat(spec.body).isEqualTo(reviewForecastSummary(forecast))
        assertThat(spec.destination).isEqualTo(NotificationDeepLink.DESTINATION_REVIEW)
    }

    @Test
    fun `reviewsAvailable uses singular phrasing for exactly one review`() {
        val forecast = ReviewForecast(reviewsAvailableNow = 1, buckets = emptyList())
        val spec = NotificationBuilder.reviewsAvailable(newCount = 1, forecast = forecast)
        assertThat(spec.title).isEqualTo("1 review is available")
    }

    @Test
    fun `reviewsBacklog targets the backlog channel and destination`() {
        val spec = NotificationBuilder.reviewsBacklog(totalDueNow = 75, threshold = 50)
        assertThat(spec.id).isEqualTo(NotificationIds.REVIEWS_BACKLOG)
        assertThat(spec.channelId).isEqualTo(NotificationChannels.REVIEWS_BACKLOG)
        assertThat(spec.destination).isEqualTo(NotificationDeepLink.DESTINATION_REVIEW)
        assertThat(spec.body).contains("75")
        assertThat(spec.body).contains("50")
    }

    @Test
    fun `lessonsAvailable targets the lesson channel and destination`() {
        val spec = NotificationBuilder.lessonsAvailable(newCount = 3, totalLessons = 3)
        assertThat(spec.id).isEqualTo(NotificationIds.LESSONS_AVAILABLE)
        assertThat(spec.channelId).isEqualTo(NotificationChannels.LESSONS_AVAILABLE)
        assertThat(spec.destination).isEqualTo(NotificationDeepLink.DESTINATION_LESSON)
        assertThat(spec.title).isEqualTo("3 new lessons are ready")
    }

    @Test
    fun `studyReminder mentions the current streak when active`() {
        val spec = NotificationBuilder.studyReminder(currentStreakDays = 12)
        assertThat(spec.channelId).isEqualTo(NotificationChannels.STUDY_REMINDER)
        assertThat(spec.destination).isEqualTo(NotificationDeepLink.DESTINATION_DASHBOARD)
        assertThat(spec.body).contains("12-day streak")
    }

    @Test
    fun `studyReminder without a streak still nudges the user`() {
        val spec = NotificationBuilder.studyReminder(currentStreakDays = 0)
        assertThat(spec.body).isEqualTo("You haven't studied today yet.")
    }

    @Test
    fun `milestone carries the given text through to the body`() {
        val spec = NotificationBuilder.milestone("You reached Level 10!")
        assertThat(spec.channelId).isEqualTo(NotificationChannels.MILESTONES)
        assertThat(spec.body).isEqualTo("You reached Level 10!")
    }
}
