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
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val OUTBOX_SYNC_WORK_NAME = "outbox_sync"

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
