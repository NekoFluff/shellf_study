package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.shared.data.OutboxSyncScheduler
import com.crazyfluff.shellfstudy.shared.sync.SyncScheduler

/** No-op stand-in for [SyncScheduler] — the real one needs WorkManager/a real Context to run. */
class FakeSyncScheduler : SyncScheduler {
    var scheduleCallCount = 0
        private set
    var cancelCallCount = 0
        private set

    override fun schedulePeriodicSync() {
        scheduleCallCount++
    }

    override fun cancelPeriodicSync() {
        cancelCallCount++
    }
}

/** No-op stand-in for [OutboxSyncScheduler] — the real one needs WorkManager/a real Context to run. */
class FakeOutboxSyncScheduler : OutboxSyncScheduler {
    var requestCount = 0
        private set

    override fun requestSync() {
        requestCount++
    }
}
