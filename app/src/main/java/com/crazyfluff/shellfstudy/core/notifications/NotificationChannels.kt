package com.crazyfluff.shellfstudy.core.notifications

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.crazyfluff.shellfstudy.shared.notifications.NotificationChannels

/** Actually creates the Android notification channels whose IDs live in the portable
 *  [NotificationChannels] object — channel creation itself needs a real Android Context. */
object AndroidNotificationChannels {
    /** Idempotent — safe to call on every app start; a no-op on API < 26. */
    fun ensureCreated(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(NotificationChannels.REVIEWS_AVAILABLE, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName("Reviews available")
                    .setDescription("Lets you know when new reviews become available.")
                    .build(),
                NotificationChannelCompat.Builder(NotificationChannels.REVIEWS_BACKLOG, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName("Review backlog")
                    .setDescription("Warns you when your review queue is piling up.")
                    .build(),
                NotificationChannelCompat.Builder(NotificationChannels.STUDY_REMINDER, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName("Daily study reminder")
                    .setDescription("A daily nudge to keep your study streak going.")
                    .build()
            )
        )
    }
}
