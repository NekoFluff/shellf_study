package com.crazyfluff.shellfstudy.core.notifications

import androidx.core.app.NotificationCompat
import com.crazyfluff.shellfstudy.core.data.model.ReviewForecast
import com.crazyfluff.shellfstudy.core.data.model.reviewForecastSummary

/**
 * Plain description of a notification to post — no [android.content.Context] dependency, so
 * [NotificationBuilder] is fully JVM-testable. [NotificationPoster] turns this into a real
 * [android.app.Notification].
 */
data class NotificationSpec(
    val id: Int,
    val channelId: String,
    val title: String,
    val body: String,
    val destination: String,
    val priority: Int = NotificationCompat.PRIORITY_DEFAULT
)

object NotificationBuilder {
    fun reviewsAvailable(newCount: Int, forecast: ReviewForecast): NotificationSpec = NotificationSpec(
        id = NotificationIds.REVIEWS_AVAILABLE,
        channelId = NotificationChannels.REVIEWS_AVAILABLE,
        title = if (newCount == 1) "1 review is available" else "$newCount reviews are available",
        body = reviewForecastSummary(forecast),
        destination = NotificationDeepLink.DESTINATION_REVIEW
    )

    fun reviewsBacklog(totalDueNow: Int, threshold: Int): NotificationSpec = NotificationSpec(
        id = NotificationIds.REVIEWS_BACKLOG,
        channelId = NotificationChannels.REVIEWS_BACKLOG,
        title = "Your review queue is piling up",
        body = "$totalDueNow reviews are waiting — that's past your $threshold-item backlog threshold.",
        destination = NotificationDeepLink.DESTINATION_REVIEW,
        priority = NotificationCompat.PRIORITY_HIGH
    )

    fun lessonsAvailable(newCount: Int, totalLessons: Int): NotificationSpec = NotificationSpec(
        id = NotificationIds.LESSONS_AVAILABLE,
        channelId = NotificationChannels.LESSONS_AVAILABLE,
        title = if (newCount == 1) "1 new lesson is ready" else "$newCount new lessons are ready",
        body = if (totalLessons == newCount) {
            "Start whenever you're ready."
        } else {
            "$totalLessons lessons waiting in total."
        },
        destination = NotificationDeepLink.DESTINATION_LESSON
    )

    fun studyReminder(currentStreakDays: Int): NotificationSpec = NotificationSpec(
        id = NotificationIds.STUDY_REMINDER,
        channelId = NotificationChannels.STUDY_REMINDER,
        title = "Keep your streak going",
        body = if (currentStreakDays > 0) {
            "You're on a $currentStreakDays-day streak — don't lose it today."
        } else {
            "You haven't studied today yet."
        },
        destination = NotificationDeepLink.DESTINATION_DASHBOARD
    )

    fun milestone(text: String): NotificationSpec = NotificationSpec(
        id = NotificationIds.MILESTONES,
        channelId = NotificationChannels.MILESTONES,
        title = "Milestone reached",
        body = text,
        destination = NotificationDeepLink.DESTINATION_DASHBOARD,
        priority = NotificationCompat.PRIORITY_LOW
    )
}
