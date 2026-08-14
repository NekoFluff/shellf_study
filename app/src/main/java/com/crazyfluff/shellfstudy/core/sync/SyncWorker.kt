package com.crazyfluff.shellfstudy.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.crazyfluff.shellfstudy.shared.data.ApiResult
import com.crazyfluff.shellfstudy.shared.data.OutboxSyncScheduler
import com.crazyfluff.shellfstudy.core.notifications.NotificationCoordinator

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val syncOrchestrator: SyncOrchestrator,
    private val notificationCoordinator: NotificationCoordinator,
    private val outboxSyncScheduler: OutboxSyncScheduler
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = when (syncOrchestrator.syncAll(force = false)) {
        is ApiResult.Success -> {
            notificationCoordinator.evaluateReviewsAndBacklog()
            notificationCoordinator.rescheduleNextReviewCheck()
            // A safety net for anything the per-mutation trigger didn't drain — confirmed online
            // at this point, so it's a cheap, correct place to also nudge the outbox.
            outboxSyncScheduler.requestSync()
            Result.success()
        }
        is ApiResult.Error -> Result.retry()
    }
}
