package com.crazyfluff.shellfstudy.shared.data

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val RETRY_DELAYS = listOf(30.seconds, 60.seconds, 120.seconds, 300.seconds)

// Matches the Android scheduler's debounce: grading a review every 1-3s coalesces into one drain
// once grading has been quiet for this long.
private val OUTBOX_SYNC_DEBOUNCE = 5.seconds

/**
 * iOS outbox scheduler: drains the outbox in the app-level coroutine scope. Unlike WorkManager on
 * Android, iOS has no OS-managed retry queue, so this class implements its own backoff: a drain that
 * returns [DrainOutcome.RETRY] (transient network failure) is retried with exponential delays
 * (30s → 60s → 120s → 300s). SUCCESS or AUTH_FAILURE end the chain.
 *
 * There is at most one [pending] job — the debounce, the drain and its retries all run inside it —
 * so a new request replaces whatever was scheduled instead of starting a second drain alongside it.
 * Cancelling a drain part-way is safe: [OutboxDrainer] records each row non-cancellably.
 */
internal class IosOutboxSyncScheduler(
    private val scope: CoroutineScope,
    private val drainer: OutboxDrainer
) : OutboxSyncScheduler {

    private var pending: Job? = null

    override fun requestSync() = schedule(initialDelay = OUTBOX_SYNC_DEBOUNCE)
    override fun requestImmediateSync() = schedule(initialDelay = Duration.ZERO)

    private fun schedule(initialDelay: Duration) {
        pending?.cancel()
        pending = scope.launch {
            delay(initialDelay)
            var attempt = 0
            while (drainer.drain() == DrainOutcome.RETRY) {
                delay(RETRY_DELAYS[attempt.coerceAtMost(RETRY_DELAYS.lastIndex)])
                attempt++
            }
        }
    }
}
