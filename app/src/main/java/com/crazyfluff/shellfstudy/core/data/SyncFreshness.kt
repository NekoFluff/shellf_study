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

/**
 * Runs the staleness-check → cursor-lookup → fetch/persist → record-success ceremony shared by
 * every resource sync — [fetchAndPersist] supplies only what's genuinely resource-specific: the
 * network call(s) and the DAO upsert. [useCursor] is false for resources with no documented
 * `updated_after` filter (e.g. level_progressions), which always do a full refetch.
 */
suspend fun runSync(
    syncStateDao: SyncStateDao,
    resource: String,
    force: Boolean,
    staleness: Duration,
    useCursor: Boolean = true,
    fetchAndPersist: suspend (cursor: String?) -> Unit
): ApiResult<Unit> {
    if (!shouldSync(syncStateDao, resource, force, staleness)) return ApiResult.Success(Unit)
    return safeApiCall {
        val cursor = if (useCursor) syncCursor(syncStateDao, resource) else null
        val startedAt = Instant.now().toString()
        fetchAndPersist(cursor)
        recordSyncSuccess(syncStateDao, resource, cursor = if (useCursor) startedAt else null)
    }
}
