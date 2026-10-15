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
private const val SCHEDULER_PREFS = "offline_audio_scheduler"
private const val LAST_WIFI_ONLY_KEY = "last_wifi_only"

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
     *  changed constraint replaces it.
     *
     *  Kept on disk rather than in memory: the queued work outlives the process, and an in-memory value
     *  read as unknown after every restart, so the first request of each process replaced — cancelling
     *  — a download that was already running under the very same constraint. */
    private val prefs by lazy { context.getSharedPreferences(SCHEDULER_PREFS, Context.MODE_PRIVATE) }

    override fun schedule(wifiOnly: Boolean) {
        val last = if (prefs.contains(LAST_WIFI_ONLY_KEY)) prefs.getBoolean(LAST_WIFI_ONLY_KEY, false) else null
        val policy = if (last == wifiOnly) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE
        prefs.edit().putBoolean(LAST_WIFI_ONLY_KEY, wifiOnly).apply()
        val network = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<AudioDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(AUDIO_DOWNLOAD_WORK_NAME, policy, request)
    }

    override fun cancel() {
        prefs.edit().remove(LAST_WIFI_ONLY_KEY).apply()
        WorkManager.getInstance(context).cancelUniqueWork(AUDIO_DOWNLOAD_WORK_NAME)
    }
}
