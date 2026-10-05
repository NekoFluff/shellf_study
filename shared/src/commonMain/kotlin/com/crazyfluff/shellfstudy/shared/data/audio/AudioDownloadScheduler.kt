package com.crazyfluff.shellfstudy.shared.data.audio

/**
 * Runs [OfflineAudioManager.runDownloads] on the platform's terms: WorkManager on Android, which holds
 * the job until a suitable network exists and retries it after a network failure; an app-scope
 * coroutine on iOS, restarted when the connection comes back.
 */
interface AudioDownloadScheduler {
    /** Runs the downloads as soon as a network is available — an unmetered one when [wifiOnly].
     *  Replaces any run already scheduled. */
    fun schedule(wifiOnly: Boolean)

    fun cancel()
}
