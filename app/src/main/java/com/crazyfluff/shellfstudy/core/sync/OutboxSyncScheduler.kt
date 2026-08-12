package com.crazyfluff.shellfstudy.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val OUTBOX_SYNC_WORK_NAME = "outbox_sync"

/**
 * Requests an outbox drain — constrained on connectivity, so calling this while offline just
 * leaves the request queued in WorkManager until the network comes back, no polling needed. An
 * interface (rather than a concrete class) for the same reason as [SyncScheduler]: ViewModel/
 * repository unit tests need a no-op fake, since the real one needs WorkManager/a real Context.
 */
interface OutboxSyncScheduler {
    fun requestSync()
}

@Singleton
class WorkManagerOutboxSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : OutboxSyncScheduler {
    override fun requestSync() {
        val request = OneTimeWorkRequestBuilder<OutboxSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(OUTBOX_SYNC_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
