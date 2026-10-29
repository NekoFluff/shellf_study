package com.crazyfluff.shellfstudy.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.crazyfluff.shellfstudy.shared.data.historysync.HistorySyncOutcome
import com.crazyfluff.shellfstudy.shared.data.historysync.HistorySyncer

/**
 * Runs one history sync pass once connectivity allows — constrained on `NetworkType.CONNECTED` by
 * [WorkManagerHistorySyncScheduler]. Cancelling it part-way is safe: see [HistorySyncer].
 */
class HistorySyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val historySyncer: HistorySyncer
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = when (historySyncer.sync()) {
        HistorySyncOutcome.SUCCESS, HistorySyncOutcome.SKIPPED -> Result.success()
        HistorySyncOutcome.RETRY -> Result.retry()
        HistorySyncOutcome.AUTH_FAILURE -> Result.failure()
    }
}
