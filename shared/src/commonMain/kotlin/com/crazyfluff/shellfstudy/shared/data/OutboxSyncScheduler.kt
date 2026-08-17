package com.crazyfluff.shellfstudy.shared.data

/**
 * Requests an outbox drain. On Android this enqueues a WorkManager job constrained on connectivity
 * (see [com.crazyfluff.shellfstudy.core.sync.WorkManagerOutboxSyncScheduler]); on iOS it launches
 * the drain in the app-level coroutine scope. Either way, safe to call while offline — transient
 * network failures are handled inside the drainer. Unit tests use a no-op fake instead.
 */
interface OutboxSyncScheduler {
    /** Coalesces bursts of calls (e.g. grading several reviews in a row) into a single drain. */
    fun requestSync()

    /** Bypasses any coalescing delay — for the moments we know the user is done and want the
     *  pending-sync UI to clear promptly (session completion, returning to the dashboard). */
    fun requestImmediateSync()
}
