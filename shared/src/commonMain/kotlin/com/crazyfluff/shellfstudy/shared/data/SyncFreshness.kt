package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.database.SyncStateDao
import com.crazyfluff.shellfstudy.shared.database.SyncStateEntity
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** Whether [resource] needs syncing now: always true when forced or never synced before. */
suspend fun shouldSync(syncStateDao: SyncStateDao, resource: String, force: Boolean, staleness: Duration): Boolean {
    if (force) return true
    val lastSuccess = syncStateDao.get(resource)?.lastSyncSuccessAt?.let(Instant::parse) ?: return true
    return Clock.System.now() > lastSuccess + staleness
}

/** Records a successful sync pass, storing [cursor] (the next `updated_after` value) if given. */
suspend fun recordSyncSuccess(syncStateDao: SyncStateDao, resource: String, cursor: String? = null) {
    val now = Clock.System.now().toString()
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
 * The [SyncStateDao] resource keys, owned here rather than privately by whichever repository happens
 * to declare each constant.
 *
 * They are shared with [com.crazyfluff.shellfstudy.shared.sync.SyncOrchestrator], which records a
 * resource's success *after* writing its rows, and a typo or a rename on one side only would create a
 * second `sync_state` row that silently never becomes fresh — every subsequent pass would refetch that
 * resource in full, forever, with no error to notice. One declaration means the two cannot disagree.
 */
object SyncResources {
    const val ASSIGNMENTS = "assignments"
    const val SUBJECTS = "subjects"
    const val SRS_SYSTEMS = "srs_systems"
    const val REVIEW_STATISTICS = "review_statistics"
    const val LEVEL_PROGRESSIONS = "level_progressions"
}

/**
 * A resource's network fetch, held apart from the database write it produces.
 *
 * Splitting fetch from write is what lets [com.crazyfluff.shellfstudy.shared.sync.SyncOrchestrator]
 * run every resource's writes inside one database transaction. Room broadcasts invalidation — and so
 * re-runs every observable query in the app — once per *outermost* write operation, so a sync pass
 * that wrote eight times woke the dashboard's dozen-odd flows eight times. The same reasoning is why
 * [cursorToRecord] travels with the fetched data rather than being written by the fetch: the cursor
 * belongs in the same transaction as the rows it describes, or a crash between the two would advance
 * the cursor past data that never landed.
 *
 * @param cursorToRecord the `updated_after` value to persist on success, or null for a resource whose
 *   fetch is not cursor-based (level progressions, which always refetch in full).
 */
class ResourceSync<T>(
    val cursorToRecord: String?,
    private val rows: T,
    private val write: suspend (T) -> Unit
) {
    /** Performs the database write. Called inside a transaction by the orchestrator, or on its own by
     *  the resource's own `sync…` entry point. */
    suspend fun persist() = write(rows)
}

/**
 * Wraps one resource's fetch as a [ResourceSync], or returns null when the resource is fresh enough
 * that nothing should be fetched.
 *
 * This is the first half of what used to be a single `runSync` block that fetched *and* persisted.
 * [completeResourceSync] is the second half.
 *
 * @param useCursor false for resources with no documented `updated_after` filter, which always do a
 *   full refetch.
 */
internal suspend fun <T> fetchResourceSync(
    syncStateDao: SyncStateDao,
    resource: String,
    force: Boolean,
    staleness: Duration,
    useCursor: Boolean = true,
    fetch: suspend (cursor: String?) -> T,
    write: suspend (T) -> Unit
): ResourceSync<T>? {
    if (!shouldSync(syncStateDao, resource, force, staleness)) return null
    val cursor = if (useCursor) syncCursor(syncStateDao, resource) else null
    // Captured before the network call, matching the original ordering: the next incremental fetch
    // must ask for everything changed since this pass *started*, or rows updated mid-fetch are lost.
    val startedAt = Clock.System.now().toString()
    val rows = fetch(cursor)
    return ResourceSync(
        cursorToRecord = if (useCursor) startedAt else null,
        rows = rows,
        write = write
    )
}

/**
 * Persists [sync]'s rows and records its cursor as one unit.
 *
 * Not transactional by itself. Callers that sync a single resource (the review/lesson queue refresh,
 * a subject detail refetch) get the same non-atomic behaviour the repository always had, since there
 * is only one write to broadcast either way; callers syncing several resources should hand this to
 * [com.crazyfluff.shellfstudy.shared.sync.SyncOrchestrator], which runs them all in one transaction.
 *
 * A resource that has no cursor to record is still persisted — [ResourceSync.cursorToRecord] being
 * null means "always refetch in full", not "nothing to do".
 */
internal suspend fun completeResourceSync(syncStateDao: SyncStateDao, resource: String, sync: ResourceSync<*>) {
    sync.persist()
    recordSyncSuccess(syncStateDao, resource, cursor = sync.cursorToRecord)
}
