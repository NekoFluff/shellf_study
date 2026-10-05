package com.crazyfluff.shellfstudy.shared.data.audio

import com.crazyfluff.shellfstudy.shared.lifecycle.IosNetwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Runs the offline audio downloads in the app-level scope. There's no OS-managed retry here as there
 * is on Android: a run that stops for lack of a network simply ends, and the connectivity monitor
 * (see IosEntry) asks for another when the network comes back. A run already going is left alone —
 * it picks up levels added while it works.
 */
internal class IosAudioDownloadScheduler(
    private val scope: CoroutineScope,
    private val runDownloads: suspend () -> AudioDownloadOutcome
) : AudioDownloadScheduler {

    private var running: Job? = null

    override fun schedule(wifiOnly: Boolean) {
        val network = IosNetwork.current
        if (!network.online || (wifiOnly && network.expensive)) return
        if (running?.isActive == true) return
        running = scope.launch { runDownloads() }
    }

    override fun cancel() {
        running?.cancel()
        running = null
    }
}
