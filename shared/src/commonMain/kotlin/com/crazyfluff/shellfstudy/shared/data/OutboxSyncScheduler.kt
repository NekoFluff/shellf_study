package com.crazyfluff.shellfstudy.shared.data

/**
 * Requests an outbox drain — constrained on connectivity, so calling this while offline just
 * leaves the request queued until the network comes back, no polling needed. An interface (rather
 * than a concrete class) since the real implementation needs WorkManager (Android-only for now —
 * see [com.crazyfluff.shellfstudy.core.sync.WorkManagerOutboxSyncScheduler]), and so ViewModel/
 * repository unit tests can use a no-op fake instead.
 */
fun interface OutboxSyncScheduler {
    fun requestSync()
}
