package com.crazyfluff.shellfstudy.core.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

private const val REVIEW_CHECK_WORK_NAME = "review_notification_check"
private const val DAILY_REMINDER_WORK_NAME = "daily_streak_reminder"
private const val DEFERRED_WORK_NAME_PREFIX = "deferred_notification_"

/** Categories that can be deferred past quiet hours via [NotificationScheduler.scheduleDeferredNotification]. */
object DeferredNotificationCategory {
    const val BACKLOG = "backlog"

    val ALL = listOf(BACKLOG)
}

/**
 * Schedules the notification system's background wakeups. An interface (like [com.crazyfluff.shellfstudy.core.sync.SyncScheduler])
 * so it can be swapped for a fake in ViewModel/coordinator unit tests, since the real
 * implementation needs WorkManager/a real Context to run.
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

@Singleton
class WorkManagerNotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : NotificationScheduler {

    override fun scheduleNextReviewCheck(targetInstant: Instant?) {
        if (targetInstant == null) {
            cancelNextReviewCheck()
            return
        }
        val request = OneTimeWorkRequestBuilder<ReviewNotificationWorker>()
            .setInitialDelay(delayUntil(targetInstant))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(REVIEW_CHECK_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancelNextReviewCheck() {
        WorkManager.getInstance(context).cancelUniqueWork(REVIEW_CHECK_WORK_NAME)
    }

    override fun scheduleDeferredNotification(category: String, targetInstant: Instant) {
        val request = OneTimeWorkRequestBuilder<DeferredNotificationWorker>()
            .setInitialDelay(delayUntil(targetInstant))
            .setInputData(workDataOf(DeferredNotificationWorker.KEY_CATEGORY to category))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(deferredWorkName(category), ExistingWorkPolicy.REPLACE, request)
    }

    override fun scheduleDailyStreakReminder(hour: Int, minute: Int) {
        val target = DailyReminderTiming.nextOccurrence(ZonedDateTime.now(), hour, minute)
        val request = OneTimeWorkRequestBuilder<DailyStreakReminderWorker>()
            .setInitialDelay(delayUntil(target))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(DAILY_REMINDER_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancelDailyStreakReminder() {
        WorkManager.getInstance(context).cancelUniqueWork(DAILY_REMINDER_WORK_NAME)
    }

    override fun cancelAll() {
        cancelNextReviewCheck()
        cancelDailyStreakReminder()
        DeferredNotificationCategory.ALL.forEach {
            WorkManager.getInstance(context).cancelUniqueWork(deferredWorkName(it))
        }
    }

    private fun delayUntil(instant: Instant): Duration =
        Duration.between(Instant.now(), instant).let { if (it.isNegative) Duration.ZERO else it }

    private fun deferredWorkName(category: String) = "$DEFERRED_WORK_NAME_PREFIX$category"
}
