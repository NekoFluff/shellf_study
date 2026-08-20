package com.crazyfluff.shellfstudy.shared.notifications

/**
 * The one genuinely Android/untestable seam of the notification system — isolated behind an
 * interface the same way [com.crazyfluff.shellfstudy.shared.sync.SyncScheduler] isolates WorkManager,
 * so [NotificationCoordinator] can be tested against a fake instead of the real
 * `NotificationManagerCompat`.
 */
interface NotificationPoster {
    fun canPost(): Boolean
    fun post(spec: NotificationSpec)
    fun cancel(id: Int)
}
