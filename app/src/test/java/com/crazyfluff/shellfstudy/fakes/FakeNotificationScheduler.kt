package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.shared.notifications.NotificationScheduler
import kotlin.time.Instant

/** No-op stand-in for [NotificationScheduler] — the real one needs WorkManager/a real Context to run. */
class FakeNotificationScheduler : NotificationScheduler {
    var nextReviewCheckInstant: Instant? = null
        private set
    var nextReviewCheckCancelled = false
        private set
    val deferredNotifications = mutableListOf<Pair<String, Instant>>()
    var dailyReminderHour: Int? = null
        private set
    var dailyReminderCancelled = false
        private set
    var cancelAllCallCount = 0
        private set

    override fun scheduleNextReviewCheck(targetInstant: Instant?) {
        nextReviewCheckInstant = targetInstant
        if (targetInstant == null) nextReviewCheckCancelled = true
    }

    override fun cancelNextReviewCheck() {
        nextReviewCheckCancelled = true
        nextReviewCheckInstant = null
    }

    override fun scheduleDeferredNotification(category: String, targetInstant: Instant) {
        deferredNotifications.add(category to targetInstant)
    }

    override fun scheduleDailyStreakReminder(hour: Int, minute: Int) {
        dailyReminderHour = hour
    }

    override fun cancelDailyStreakReminder() {
        dailyReminderCancelled = true
        dailyReminderHour = null
    }

    override fun cancelAll() {
        cancelAllCallCount++
        nextReviewCheckCancelled = true
        dailyReminderCancelled = true
    }
}
