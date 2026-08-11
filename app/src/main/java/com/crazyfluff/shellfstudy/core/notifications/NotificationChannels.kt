package com.crazyfluff.shellfstudy.core.notifications

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat

object NotificationChannels {
    const val REVIEWS_AVAILABLE = "reviews_available"
    const val REVIEWS_BACKLOG = "reviews_backlog"
    const val STUDY_REMINDER = "study_reminder"

    /** Idempotent — safe to call on every app start; a no-op on API < 26. */
    fun ensureCreated(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(REVIEWS_AVAILABLE, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName("Reviews available")
                    .setDescription("Lets you know when new reviews become available.")
                    .build(),
                NotificationChannelCompat.Builder(REVIEWS_BACKLOG, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName("Review backlog")
                    .setDescription("Warns you when your review queue is piling up.")
                    .build(),
                NotificationChannelCompat.Builder(STUDY_REMINDER, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName("Daily study reminder")
                    .setDescription("A daily nudge to keep your study streak going.")
                    .build()
            )
        )
    }
}

object NotificationIds {
    const val REVIEWS_AVAILABLE = 1001
    const val REVIEWS_BACKLOG = 1002
    const val STUDY_REMINDER = 1004
}
