package com.crazyfluff.shellfstudy.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration

private const val SYNC_WORK_NAME = "wanikani_sync"

/**
 * Schedules the hourly background sync — enqueued on login, cancelled on logout. An interface
 * (rather than a concrete class) so ViewModel unit tests can supply a no-op fake instead of
 * exercising the real WorkManager, which needs a real Android Context to run.
 */
interface SyncScheduler {
    fun schedulePeriodicSync()
    fun cancelPeriodicSync()
}

class WorkManagerSyncScheduler(
    private val context: Context
) : SyncScheduler {
    override fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            repeatInterval = Duration.ofHours(1),
            flexTimeInterval = Duration.ofMinutes(5)
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(SYNC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancelPeriodicSync() {
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
    }
}
