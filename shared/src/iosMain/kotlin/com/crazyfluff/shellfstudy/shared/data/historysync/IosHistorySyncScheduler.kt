package com.crazyfluff.shellfstudy.shared.data.historysync

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val RETRY_DELAYS = listOf(60.seconds, 120.seconds, 300.seconds, 600.seconds)

// Matches the Android scheduler's debounce.
private val HISTORY_SYNC_DEBOUNCE = 10.seconds

/**
 * iOS history-sync scheduler: runs the pass in the app-level coroutine scope, with its own backoff
 * since iOS has no WorkManager. Like [com.crazyfluff.shellfstudy.shared.data.IosOutboxSyncScheduler],
 * there is at most one [pending] job, and a new request replaces it. Cancelling a pass part-way is
 * safe: see [HistorySyncer].
 *
 * [syncer] is resolved per pass rather than injected directly, because [HistorySyncer] depends on
 * this scheduler (turning sync on requests a pass).
 */
internal class IosHistorySyncScheduler(
    private val scope: CoroutineScope,
    private val syncer: () -> HistorySyncer
) : HistorySyncScheduler {

    private var pending: Job? = null

    override fun requestSync() = schedule(initialDelay = HISTORY_SYNC_DEBOUNCE)

    override fun requestImmediateSync() = schedule(initialDelay = Duration.ZERO)

    private fun schedule(initialDelay: Duration) {
        pending?.cancel()
        pending = scope.launch {
            delay(initialDelay)
            var attempt = 0
            while (syncer().sync() == HistorySyncOutcome.RETRY && attempt < RETRY_DELAYS.size) {
                delay(RETRY_DELAYS[attempt])
                attempt++
            }
        }
    }
}
