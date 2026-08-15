package com.crazyfluff.shellfstudy.core.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.crazyfluff.shellfstudy.shared.notifications.DeferredNotificationCategory
import com.crazyfluff.shellfstudy.shared.notifications.NotificationCoordinator

/**
 * Re-evaluates a category (currently just backlog) once quiet hours end, for a notification
 * that was suppressed rather than dropped. Re-reads live state instead of trusting counts
 * captured when the deferral was scheduled, since time has passed.
 */
class DeferredNotificationWorker(
    appContext: Context,
    params: WorkerParameters,
    private val notificationCoordinator: NotificationCoordinator
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        when (inputData.getString(KEY_CATEGORY)) {
            DeferredNotificationCategory.BACKLOG -> notificationCoordinator.evaluateReviewsAndBacklog()
        }
        return Result.success()
    }

    companion object {
        const val KEY_CATEGORY = "category"
    }
}
