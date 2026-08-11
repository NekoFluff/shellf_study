package com.crazyfluff.shellfstudy.core.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Re-evaluates a category (currently just backlog) once quiet hours end, for a notification
 * that was suppressed rather than dropped. Re-reads live state instead of trusting counts
 * captured when the deferral was scheduled, since time has passed.
 */
@HiltWorker
class DeferredNotificationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
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
