package com.crazyfluff.shellfstudy.core.data

import com.crazyfluff.shellfstudy.core.database.SyncStateDao
import com.crazyfluff.shellfstudy.core.database.SyncStateEntity
import java.time.Duration
import java.time.Instant

/** Whether [resource] needs syncing now: always true when forced or never synced before. */
suspend fun shouldSync(syncStateDao: SyncStateDao, resource: String, force: Boolean, staleness: Duration): Boolean {
    if (force) return true
    val lastSuccess = syncStateDao.get(resource)?.lastSyncSuccessAt?.let(Instant::parse) ?: return true
    return Instant.now().isAfter(lastSuccess.plus(staleness))
}

/** Records a successful sync pass, storing [cursor] (the next `updated_after` value) if given. */
suspend fun recordSyncSuccess(syncStateDao: SyncStateDao, resource: String, cursor: String? = null) {
    val now = Instant.now().toString()
    val previous = syncStateDao.get(resource)
    syncStateDao.upsert(
        SyncStateEntity(
            resource = resource,
            lastSyncedAt = cursor ?: previous?.lastSyncedAt,
            lastSyncAttemptAt = now,
            lastSyncSuccessAt = now
        )
    )
}

/** The `updated_after` cursor to use for [resource]'s next incremental fetch, or null for a full fetch. */
suspend fun syncCursor(syncStateDao: SyncStateDao, resource: String): String? =
    syncStateDao.get(resource)?.lastSyncedAt
