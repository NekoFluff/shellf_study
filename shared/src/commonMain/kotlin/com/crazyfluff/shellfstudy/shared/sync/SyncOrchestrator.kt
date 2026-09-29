package com.crazyfluff.shellfstudy.shared.sync

import com.crazyfluff.shellfstudy.shared.data.ApiResult
import com.crazyfluff.shellfstudy.shared.data.AssignmentRepository
import com.crazyfluff.shellfstudy.shared.data.ResourceSync
import com.crazyfluff.shellfstudy.shared.data.StatsRepository
import com.crazyfluff.shellfstudy.shared.data.safeApiCall
import com.crazyfluff.shellfstudy.shared.data.ASSIGNMENTS_RESUME_STALENESS
import com.crazyfluff.shellfstudy.shared.data.ASSIGNMENTS_STALENESS
import com.crazyfluff.shellfstudy.shared.data.SyncResources
import com.crazyfluff.shellfstudy.shared.data.SubjectRepository
import com.crazyfluff.shellfstudy.shared.data.completeResourceSync
import com.crazyfluff.shellfstudy.shared.database.SyncStateDao
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The single place that sequences a full sync pass across every repository — both the periodic
 * `SyncWorker` and manual triggers (app open, pull-to-refresh, and [fullRefresh]'s cursor reset)
 * call this instead of duplicating the ordering themselves.
 */
class SyncOrchestrator(
    private val transactionRunner: SyncTransactionRunner,
    private val subjectRepository: SubjectRepository,
    private val assignmentRepository: AssignmentRepository,
    private val statsRepository: StatsRepository,
    private val syncStateDao: SyncStateDao
) {
    // syncAll and fullRefresh have independent, legitimate entry points (periodic worker, dashboard
    // resume/pull-to-refresh, and a manual full-refresh button) that can otherwise overlap in time.
    // Without serializing them, fullRefresh's cursor clear can race a concurrent syncAll's read of
    // the very cursors it just cleared, corrupting which `updated_after` value ends up persisted.
    private val syncMutex = Mutex()

    suspend fun syncAll(force: Boolean = false): ApiResult<Unit> = trackingSyncActivity {
        syncMutex.withLock { syncAllLocked(force = force, resumeAssignments = false) }
    }

    /**
     * [syncAll] for the dashboard's resume path: assignments on a short freshness window, every other
     * resource left on its normal one.
     *
     * The resume path has to keep the forecast, item-spread and level-progress cards level with the
     * banner counts, which come from `/summary` and are already fresh by the time those cards render.
     * `syncAll(force = false)` alone would let assignments trail by up to
     * [com.crazyfluff.shellfstudy.shared.data.ASSIGNMENTS_STALENESS], so the resume path used a shorter
     * window instead.
     *
     * It previously *forced* assignments outright, which bypasses the staleness gate completely. That
     * gate is not a nicety: the write re-inserts every returned row with `INSERT OR REPLACE`, and on an
     * account with a few thousand assignments that is thousands of row rewrites maintaining five
     * indexes each, on every single return to the dashboard — measured as the session's worst frames,
     * 272 ms and 244 ms, both on a fully-loaded dashboard. Nothing needed refreshing in either case;
     * the user had just come back from grading, and the optimistic local write had already put their
     * progress on screen.
     *
     * The name is kept from when it forced, because the distinction it encodes — "this is the resume
     * path, treat assignments differently" — is still the point.
     */
    suspend fun syncAllForResume(): ApiResult<Unit> = trackingSyncActivity {
        syncMutex.withLock { syncAllLocked(force = false, resumeAssignments = true) }
    }

    /**
     * What the lesson and review screens need before they can build a queue: assignments, and the
     * subjects and SRS systems they resolve against — without the queue load waiting on (or failing
     * over) statistics it does not show. Every resource stays on its normal freshness window.
     *
     * Takes the same lock as [syncAll], so it can never interleave with a pass writing the same cursors.
     */
    suspend fun syncQueue(): ApiResult<Unit> = trackingSyncActivity {
        syncMutex.withLock { syncAllLocked(force = false, resumeAssignments = false, resources = QUEUE_RESOURCES) }
    }

    /**
     * Runs [block] with [SyncActivity] marked as syncing for its whole duration.
     *
     * `finally` rather than a success path: a pass that fails, or is cancelled, is still over, and
     * leaving the flag stuck on would tag every later frame `sync=inflight` and make the harness
     * actively misleading — worse than not having the tag at all.
     */
    private suspend fun <T> trackingSyncActivity(block: suspend () -> T): T {
        SyncActivity.passStarted()
        return try {
            block()
        } finally {
            SyncActivity.passFinished()
        }
    }

    /**
     * One pass: fetch every stale resource concurrently, then persist them all inside a single
     * database transaction.
     *
     * ## Why the writes share one transaction
     *
     * Room broadcasts invalidation — and so re-runs every observable query in the app — once per
     * *outermost* write operation. `DBUtil.internalPerform` skips `invalidationTracker.refreshAsync()`
     * when the transactor is already inside a transaction, and `RoomDatabase.useWriterConnection`
     * refreshes once when the connection is released. A pass that wrote eight times separately
     * therefore woke the dashboard's dozen-odd observable flows eight times, and each of those
     * re-ran its query — several of them full scans of a table holding thousands of rows. Grouping
     * the writes means one wake-up for the whole pass.
     *
     * ## Why the fetches are not inside it
     *
     * A Room database has exactly one write connection, and holding it across network I/O would block
     * every other writer in the app for the duration of a paged fetch — and can throw outright if the
     * connection is held past its timeout. So the fetches all complete first, and only then is the
     * connection taken, for as long as the writes actually need. That is why the repositories expose
     * their fetch and their write separately.
     *
     * ## Ordering
     *
     * Subjects and SRS systems are fetched first because everything else references subject IDs
     * (subjects reference `spaced_repetition_system_id`). Within the persist they are written in that
     * same order, so a concurrent reader inside the transaction never observes an assignment whose
     * subject row does not exist yet.
     */
    private suspend fun syncAllLocked(
        force: Boolean,
        resumeAssignments: Boolean,
        resources: Set<String> = ALL_RESOURCES
    ): ApiResult<Unit> {
        // Each fetch is wrapped individually rather than letting the coroutineScope propagate the
        // first failure. `awaitAll` over bare `async` blocks aborts the whole scope on the first
        // throw, cancelling its siblings — so one failing resource would silently stop the others
        // being fetched *or* written, the opposite of the contract this pass has always had (see
        // SyncOrchestratorTest: one resource failing still leaves the rest synced). A resource that
        // is fresh enough to skip yields Success(null) and contributes nothing.
        val fetchers: List<ResourceFetch> = listOf(
            ResourceFetch(SyncResources.SRS_SYSTEMS) { subjectRepository.fetchSrsSystems(force) },
            ResourceFetch(SyncResources.SUBJECTS) { subjectRepository.fetchSubjects(force) },
            ResourceFetch(SyncResources.ASSIGNMENTS) {
                assignmentRepository.fetchAssignments(
                    force = force,
                    // The resume path gets a short window rather than an unconditional force — see
                    // syncAllForResume.
                    staleness = if (resumeAssignments) ASSIGNMENTS_RESUME_STALENESS else ASSIGNMENTS_STALENESS
                )
            },
            ResourceFetch(SyncResources.REVIEW_STATISTICS) { statsRepository.fetchReviewStatistics(force) },
            ResourceFetch(SyncResources.LEVEL_PROGRESSIONS) { statsRepository.fetchLevelProgressions(force) }
        ).filter { it.resource in resources }

        val fetched: List<ApiResult<ResourceSync<*>?>> = coroutineScope {
            fetchers.map { async { safeApiCall { it.fetch() } } }.awaitAll()
        }

        // Written in dependency order — the order [fetchers] lists them in. SRS systems before subjects
        // (subjects reference them), subjects before assignments and statistics (both resolve subject
        // content), and level progressions last as they reference nothing.
        val writes: List<Pair<String, ResourceSync<*>>> = fetchers.zip(fetched).mapNotNull { (fetcher, result) ->
            (result as? ApiResult.Success)?.data?.let { fetcher.resource to it }
        }

        // Whatever did arrive is still written. Reporting a failure without persisting the successes
        // would discard work already paid for over the network, and the next pass would refetch it.
        val writeResult: ApiResult<Unit> = if (writes.isEmpty()) {
            ApiResult.Success(Unit)
        } else {
            safeApiCall {
                transactionRunner.runInTransaction {
                    writes.forEach { (resource, sync) -> completeResourceSync(syncStateDao, resource, sync) }
                }
            }
        }

        // A fetch failure is reported in preference to a write failure: it is the one the user can
        // act on (connectivity, token), and a failed write usually shares its cause.
        return fetched.filterIsInstance<ApiResult.Error>().firstOrNull() ?: writeResult
    }

    /**
     * `syncAll(force = true)` still reuses each resource's saved `updated_after` cursor — it only
     * skips the staleness check, so anything WaniKani hasn't itself touched since the last sync
     * never comes back. This clears every cursor first, so the following [syncAll] is a genuine
     * `updated_after=null` full refetch — for recovering from a local mapping bug (data that's
     * wrong on-device despite being unchanged on WaniKani), not routine use.
     */
    suspend fun fullRefresh(): ApiResult<Unit> = trackingSyncActivity {
        syncMutex.withLock {
            syncStateDao.clearAll()
            syncAllLocked(force = true, resumeAssignments = false)
        }
    }
}

private class ResourceFetch(val resource: String, val fetch: suspend () -> ResourceSync<*>?)

private val ALL_RESOURCES = setOf(
    SyncResources.SRS_SYSTEMS,
    SyncResources.SUBJECTS,
    SyncResources.ASSIGNMENTS,
    SyncResources.REVIEW_STATISTICS,
    SyncResources.LEVEL_PROGRESSIONS
)

private val QUEUE_RESOURCES = setOf(SyncResources.SRS_SYSTEMS, SyncResources.SUBJECTS, SyncResources.ASSIGNMENTS)
