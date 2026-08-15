package com.crazyfluff.shellfstudy.shared.notifications

import kotlin.time.Instant

/** Categories that can be deferred past quiet hours via [NotificationScheduler.scheduleDeferredNotification]. */
object DeferredNotificationCategory {
    const val BACKLOG = "backlog"

    val ALL = listOf(BACKLOG)
}

/**
 * Schedules the notification system's background wakeups. An interface (like
 * [com.crazyfluff.shellfstudy.core.sync.SyncScheduler]) so it can be swapped for a fake in
 * ViewModel/coordinator unit tests, since the real implementation needs WorkManager/a real
 * Context to run.
 */
interface NotificationScheduler {
    /** Schedules a precisely-timed check for when the next batch of reviews becomes available. Pass null to cancel. */
    fun scheduleNextReviewCheck(targetInstant: Instant?)
    fun cancelNextReviewCheck()

    /** Re-evaluates [category] once quiet hours end, instead of dropping a suppressed notification. */
    fun scheduleDeferredNotification(category: String, targetInstant: Instant)

    fun scheduleDailyStreakReminder(hour: Int, minute: Int = 0)
    fun cancelDailyStreakReminder()

    /** Cancels every scheduled notification wakeup — called on logout. */
    fun cancelAll()
}
