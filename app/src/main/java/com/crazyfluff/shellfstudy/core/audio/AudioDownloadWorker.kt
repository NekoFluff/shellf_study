package com.crazyfluff.shellfstudy.core.audio

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.crazyfluff.shellfstudy.shared.data.audio.AudioDownloadOutcome
import com.crazyfluff.shellfstudy.shared.data.audio.AudioDownloadScheduler
import com.crazyfluff.shellfstudy.shared.data.audio.OfflineAudioManager
import java.util.concurrent.TimeUnit

private const val AUDIO_DOWNLOAD_WORK_NAME = "offline_audio_downloads"

/** Downloads the offline audio library's missing clips — see [OfflineAudioManager.runDownloads]. A
 *  network failure part-way is a retry: WorkManager runs it again once the constraint holds, and the
 *  clips already stored are skipped. */
class AudioDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
    private val offlineAudioManager: OfflineAudioManager
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = when (offlineAudioManager.runDownloads()) {
        AudioDownloadOutcome.COMPLETE -> Result.success()
        AudioDownloadOutcome.NETWORK_FAILURE -> Result.retry()
    }
}

class WorkManagerAudioDownloadScheduler(
    private val context: Context
) : AudioDownloadScheduler {

    /** The network constraint the last request was made with. A request with the same one keeps
     *  whatever is already queued or running — a run picks up levels added while it works — and only a
     *  changed constraint replaces it. */
    private var lastWifiOnly: Boolean? = null

    override fun schedule(wifiOnly: Boolean) {
        val policy = if (lastWifiOnly == wifiOnly) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE
        lastWifiOnly = wifiOnly
        val network = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<AudioDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(AUDIO_DOWNLOAD_WORK_NAME, policy, request)
    }

    override fun cancel() {
        lastWifiOnly = null
        WorkManager.getInstance(context).cancelUniqueWork(AUDIO_DOWNLOAD_WORK_NAME)
    }
}
