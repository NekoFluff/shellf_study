package com.crazyfluff.shellfstudy.core.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.crazyfluff.shellfstudy.shared.notifications.DeferredNotificationCategory
import com.crazyfluff.shellfstudy.shared.notifications.DailyReminderTiming
import com.crazyfluff.shellfstudy.shared.notifications.NotificationScheduler
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.time.Duration
import java.time.Instant
import kotlin.time.Clock
import kotlin.time.toJavaInstant

private const val REVIEW_CHECK_WORK_NAME = "review_notification_check"
private const val DAILY_REMINDER_WORK_NAME = "daily_streak_reminder"
private const val DEFERRED_WORK_NAME_PREFIX = "deferred_notification_"

class WorkManagerNotificationScheduler(
    private val context: Context
) : NotificationScheduler {

    override fun scheduleNextReviewCheck(targetInstant: kotlin.time.Instant?) {
        if (targetInstant == null) {
            cancelNextReviewCheck()
            return
        }
        val request = OneTimeWorkRequestBuilder<ReviewNotificationWorker>()
            .setInitialDelay(delayUntil(targetInstant.toJavaInstant()))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(REVIEW_CHECK_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancelNextReviewCheck() {
        WorkManager.getInstance(context).cancelUniqueWork(REVIEW_CHECK_WORK_NAME)
    }

    override fun scheduleDeferredNotification(category: String, targetInstant: kotlin.time.Instant) {
        val request = OneTimeWorkRequestBuilder<DeferredNotificationWorker>()
            .setInitialDelay(delayUntil(targetInstant.toJavaInstant()))
            .setInputData(workDataOf(DeferredNotificationWorker.KEY_CATEGORY to category))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(deferredWorkName(category), ExistingWorkPolicy.REPLACE, request)
    }

    override fun scheduleDailyStreakReminder(hour: Int, minute: Int) {
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now().toLocalDateTime(zone)
        val target = DailyReminderTiming.nextOccurrence(now, zone, hour, minute)
        val request = OneTimeWorkRequestBuilder<DailyStreakReminderWorker>()
            .setInitialDelay(delayUntil(target.toJavaInstant()))
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
