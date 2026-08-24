package com.crazyfluff.shellfstudy.shared.notifications

import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecast
import com.crazyfluff.shellfstudy.shared.data.model.reviewForecastSummary

/** Mirrors `androidx.core.app.NotificationCompat`'s priority levels without depending on it —
 *  [com.crazyfluff.shellfstudy.core.notifications.SystemNotificationPoster] maps this back to the
 *  real Android constant when actually posting. */
enum class NotificationPriority { DEFAULT, HIGH }

/**
 * Plain description of a notification to post — no Android dependency, so [NotificationBuilder]
 * is fully JVM-testable. The platform-specific poster turns this into a real notification.
 */
data class NotificationSpec(
    val id: Int,
    val channelId: String,
    val title: String,
    val body: String,
    val destination: String,
    val priority: NotificationPriority = NotificationPriority.DEFAULT
)

object NotificationBuilder {
    fun reviewsAvailable(forecast: ReviewForecast): NotificationSpec = NotificationSpec(
        id = NotificationIds.REVIEWS_AVAILABLE,
        channelId = NotificationChannels.REVIEWS_AVAILABLE,
        title = "Reviews are ready for you",
        body = reviewForecastSummary(forecast),
        destination = NotificationDeepLink.DESTINATION_DASHBOARD
    )

    fun reviewsBacklog(totalDueNow: Int, threshold: Int): NotificationSpec = NotificationSpec(
        id = NotificationIds.REVIEWS_BACKLOG,
        channelId = NotificationChannels.REVIEWS_BACKLOG,
        title = "Your reviews miss you",
        body = "$totalDueNow are waiting — more than usual. A few rounds now will make a big dent.",
        destination = NotificationDeepLink.DESTINATION_DASHBOARD,
        priority = NotificationPriority.HIGH
    )

    fun studyReminder(currentStreakDays: Int): NotificationSpec = NotificationSpec(
        id = NotificationIds.STUDY_REMINDER,
        channelId = NotificationChannels.STUDY_REMINDER,
        title = if (currentStreakDays > 0) "Keep your streak going" else "Ready to study?",
        body = if (currentStreakDays > 0) {
            "You're on a $currentStreakDays-day streak — don't lose it today."
        } else {
            "A quick session today gets your streak started."
        },
        destination = NotificationDeepLink.DESTINATION_DASHBOARD
    )
}
