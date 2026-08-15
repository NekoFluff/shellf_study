package com.crazyfluff.shellfstudy.shared.notifications

/** Channel ID constants — the actual channel-creation call
 *  (`android.app.NotificationManager`/`NotificationChannelCompat`) is Android-only and lives in
 *  :app's own AndroidNotificationChannels.kt. */
object NotificationChannels {
    const val REVIEWS_AVAILABLE = "reviews_available"
    const val REVIEWS_BACKLOG = "reviews_backlog"
    const val STUDY_REMINDER = "study_reminder"
}

object NotificationIds {
    const val REVIEWS_AVAILABLE = 1001
    const val REVIEWS_BACKLOG = 1002
    const val STUDY_REMINDER = 1004
}
