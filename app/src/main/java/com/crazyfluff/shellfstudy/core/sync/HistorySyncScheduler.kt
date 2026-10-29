package com.crazyfluff.shellfstudy.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.crazyfluff.shellfstudy.shared.data.historysync.HistorySyncScheduler
import java.time.Duration

private const val HISTORY_SYNC_WORK_NAME = "history_sync"

// Coalesces the triggers that tend to arrive together (a session ending, then the dashboard
// reopening) into one pass: each request REPLACEs the still-delayed one, resetting the timer.
private val HISTORY_SYNC_DEBOUNCE = Duration.ofSeconds(10)

class WorkManagerHistorySyncScheduler(
    private val context: Context
) : HistorySyncScheduler {
    override fun requestSync() {
        val request = OneTimeWorkRequestBuilder<HistorySyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(HISTORY_SYNC_DEBOUNCE)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(1))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(HISTORY_SYNC_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
