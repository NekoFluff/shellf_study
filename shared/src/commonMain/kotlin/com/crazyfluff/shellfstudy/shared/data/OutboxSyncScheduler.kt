package com.crazyfluff.shellfstudy.shared.data

/**
 * Requests an outbox drain. On Android this enqueues a WorkManager job constrained on connectivity
 * (see [com.crazyfluff.shellfstudy.core.sync.WorkManagerOutboxSyncScheduler]); on iOS it launches
 * the drain in the app-level coroutine scope. Either way, safe to call while offline — transient
 * network failures are handled inside the drainer. Unit tests use a no-op fake instead.
 */
fun interface OutboxSyncScheduler {
    fun requestSync()
}
