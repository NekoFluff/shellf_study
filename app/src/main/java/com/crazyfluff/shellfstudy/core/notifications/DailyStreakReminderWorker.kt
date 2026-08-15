package com.crazyfluff.shellfstudy.core.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.crazyfluff.shellfstudy.shared.notifications.NotificationCoordinator

/**
 * Fires once at the user's chosen local hour, posts a reminder if they haven't studied yet today,
 * then re-schedules itself for tomorrow — see [DailyReminderTiming] for why a plain
 * [androidx.work.PeriodicWorkRequest] can't do this (it can't pin to a wall-clock hour or survive DST).
 */
class DailyStreakReminderWorker(
    appContext: Context,
    params: WorkerParameters,
    private val notificationCoordinator: NotificationCoordinator
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        notificationCoordinator.evaluateStudyReminder()
        notificationCoordinator.rescheduleDailyReminder()
        return Result.success()
    }
}
