package com.crazyfluff.shellfstudy.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.crazyfluff.shellfstudy.core.data.ApiResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncOrchestrator: SyncOrchestrator
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = when (syncOrchestrator.syncAll(force = false)) {
        is ApiResult.Success -> Result.success()
        is ApiResult.Error -> Result.retry()
    }
}
