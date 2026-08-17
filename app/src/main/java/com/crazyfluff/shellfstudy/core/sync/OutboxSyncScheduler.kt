package com.crazyfluff.shellfstudy.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.crazyfluff.shellfstudy.shared.data.OutboxSyncScheduler
import java.time.Duration
import java.util.concurrent.TimeUnit

private const val OUTBOX_SYNC_WORK_NAME = "outbox_sync"

// Coalesces bursts of requestSync() calls (e.g. grading a review every 1-3s) into a single drain:
// each call REPLACEs the still-delayed pending request, resetting the timer, so the worker only
// actually runs once grading has been quiet for this long.
private val OUTBOX_SYNC_DEBOUNCE = Duration.ofSeconds(5)

class WorkManagerOutboxSyncScheduler(
    private val context: Context
) : OutboxSyncScheduler {
    override fun requestSync() = enqueue(initialDelay = OUTBOX_SYNC_DEBOUNCE)

    override fun requestImmediateSync() = enqueue(initialDelay = Duration.ZERO)

    private fun enqueue(initialDelay: Duration) {
        val request = OneTimeWorkRequestBuilder<OutboxSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(initialDelay)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(OUTBOX_SYNC_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
