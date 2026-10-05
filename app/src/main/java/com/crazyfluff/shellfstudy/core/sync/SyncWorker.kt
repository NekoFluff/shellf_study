package com.crazyfluff.shellfstudy.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.crazyfluff.shellfstudy.shared.data.ApiResult
import com.crazyfluff.shellfstudy.shared.data.OutboxDrainer
import com.crazyfluff.shellfstudy.shared.data.OutboxSyncScheduler
import com.crazyfluff.shellfstudy.shared.notifications.NotificationCoordinator
import com.crazyfluff.shellfstudy.shared.sync.SyncOrchestrator

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val syncOrchestrator: SyncOrchestrator,
    private val outboxDrainer: OutboxDrainer,
    private val notificationCoordinator: NotificationCoordinator,
    private val outboxSyncScheduler: OutboxSyncScheduler
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Drain before syncing, so the pass fetches assignments WaniKani has already applied this
        // device's queued work to. The outcome is not this worker's concern: a failed drain leaves its
        // rows queued for the outbox's own worker, and the sync skips those rows' assignments rather
        // than overwriting their local progress (see AssignmentRepository.fetchAssignments).
        outboxDrainer.drain()
        return sync()
    }

    private suspend fun sync(): Result = when (syncOrchestrator.syncAll(force = false)) {
        is ApiResult.Success -> {
            notificationCoordinator.evaluateReviewsAndBacklog()
            notificationCoordinator.rescheduleNextReviewCheck()
            notificationCoordinator.rescheduleLevelUpReminder()
            // A safety net for anything the per-mutation trigger didn't drain — confirmed online
            // at this point, so it's a cheap, correct place to also nudge the outbox.
            outboxSyncScheduler.requestSync()
            Result.success()
        }
        is ApiResult.Error -> Result.retry()
    }
}
