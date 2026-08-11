package com.crazyfluff.shellfstudy.core.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Fires when the next batch of reviews is predicted to become available (scheduled precisely by
 * [NotificationCoordinator.rescheduleNextReviewCheck], not on the hourly sync cadence). Evaluates
 * with fresh data and re-schedules itself for the following batch, forming a self-perpetuating
 * chain between full syncs.
 */
@HiltWorker
class ReviewNotificationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val notificationCoordinator: NotificationCoordinator
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        notificationCoordinator.evaluateReviewsAndBacklog()
        notificationCoordinator.rescheduleNextReviewCheck()
        return Result.success()
    }
}
