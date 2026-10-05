package com.crazyfluff.shellfstudy.core.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.crazyfluff.shellfstudy.shared.notifications.DeferredNotificationCategory
import com.crazyfluff.shellfstudy.shared.notifications.NotificationCoordinator

/**
 * Re-evaluates a category once quiet hours end, for a notification that was suppressed rather
 * than dropped — or, for [DeferredNotificationCategory.LEVEL_UP], at the time the next deciding
 * review comes due. Re-reads live state instead of trusting counts
 * captured when the deferral was scheduled, since time has passed.
 */
class DeferredNotificationWorker(
    appContext: Context,
    params: WorkerParameters,
    private val notificationCoordinator: NotificationCoordinator
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = retryOnFailure {
        when (inputData.getString(KEY_CATEGORY)) {
            DeferredNotificationCategory.BACKLOG -> notificationCoordinator.evaluateReviewsAndBacklog()
            DeferredNotificationCategory.STUDY_REMINDER -> notificationCoordinator.evaluateStudyReminder()
            DeferredNotificationCategory.LEVEL_UP -> notificationCoordinator.evaluateLevelUpReminder()
        }
    }

    companion object {
        const val KEY_CATEGORY = "category"
    }
}
