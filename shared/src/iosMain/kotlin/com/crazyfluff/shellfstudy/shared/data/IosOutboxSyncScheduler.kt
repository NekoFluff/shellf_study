package com.crazyfluff.shellfstudy.shared.data

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val RETRY_DELAYS = listOf(30.seconds, 60.seconds, 120.seconds, 300.seconds)

/**
 * iOS outbox scheduler: drains the outbox in the app-level coroutine scope. Unlike WorkManager on
 * Android, iOS has no OS-managed retry queue, so this class implements its own backoff: if a drain
 * returns [DrainOutcome.RETRY] (transient network failure), it reschedules automatically with
 * exponential delays (30s → 60s → 120s → 300s). SUCCESS or AUTH_FAILURE clear the retry chain.
 * Calling [requestSync]/[requestImmediateSync] while a retry is pending cancels the pending retry
 * and runs a drain immediately, resetting the backoff counter.
 */
internal class IosOutboxSyncScheduler(
    private val scope: CoroutineScope,
    private val drainer: OutboxDrainer
) : OutboxSyncScheduler {

    private var retryJob: Job? = null
    private var retryIndex = 0

    override fun requestSync() = drain()
    override fun requestImmediateSync() = drain()

    private fun drain() {
        retryJob?.cancel()
        retryIndex = 0
        scope.launch { runDrainWithRetry() }
    }

    private suspend fun runDrainWithRetry() {
        when (drainer.drain()) {
            DrainOutcome.SUCCESS, DrainOutcome.AUTH_FAILURE -> retryIndex = 0
            DrainOutcome.RETRY -> scheduleRetry()
        }
    }

    private fun scheduleRetry() {
        val delayDuration = RETRY_DELAYS.getOrElse(retryIndex) { RETRY_DELAYS.last() }
        retryIndex = (retryIndex + 1).coerceAtMost(RETRY_DELAYS.size)
        retryJob = scope.launch {
            delay(delayDuration)
            runDrainWithRetry()
        }
    }
}
