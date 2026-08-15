package com.crazyfluff.shellfstudy.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.crazyfluff.shellfstudy.shared.data.DrainOutcome
import com.crazyfluff.shellfstudy.shared.data.OutboxDrainer

/**
 * Drains the durable outbox (queued review submissions / lesson starts) once connectivity allows
 * — constrained on `NetworkType.CONNECTED` by [OutboxSyncScheduler], so this only ever runs when
 * actually online. Lesson starts are drained before review submissions (a review's assignment
 * must already be started, so this is the natural precedence), each in creation order.
 */
class OutboxSyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val outboxDrainer: OutboxDrainer,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = when (outboxDrainer.drain()) {
        DrainOutcome.SUCCESS -> Result.success()
        DrainOutcome.RETRY -> Result.retry()
        DrainOutcome.AUTH_FAILURE -> Result.failure()
    }
}

