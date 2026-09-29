package com.crazyfluff.shellfstudy.shared.sync

import com.crazyfluff.shellfstudy.shared.data.ApiResult
import com.crazyfluff.shellfstudy.shared.data.AssignmentRepository
import com.crazyfluff.shellfstudy.shared.data.ResourceSync
import com.crazyfluff.shellfstudy.shared.data.StatsRepository
import com.crazyfluff.shellfstudy.shared.data.safeApiCall
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

    suspend fun syncAll(force: Boolean = false): ApiResult<Unit> = syncMutex.withLock {
        syncAllLocked(force = force, forceAssignments = false)
    }

    /**
     * [syncAll] with assignments forced and every other resource left staleness-gated.
     *
     * The dashboard's resume path needs exactly that combination: it refetches the banner counts from
     * `/summary` on every resume, but `syncAll(force = false)` only refetches assignments once
     * `ASSIGNMENTS_STALENESS` has elapsed — leaving the forecast, item-spread and level-progress cards
     * (all derived from the local assignments table) trailing the banner by up to an hour.
     *
     * This exists as a parameter rather than as "call `syncAll(force = false)` and then also call
     * `assignmentRepository.syncAssignments(force = true)`", which is what the dashboard used to do.
     * That fetched and rewrote the assignments table twice on every resume — the second pass superseding
     * the first — and each rewrite is its own Room write operation and therefore its own invalidation
     * broadcast to every observable assignments query. Forcing the resource *within* the pass still
     * reuses its saved `updated_after` cursor, so this stays an incremental fetch, not a full resync.
     */
    suspend fun syncAllForcingAssignments(): ApiResult<Unit> = syncMutex.withLock {
        syncAllLocked(force = false, forceAssignments = true)
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
    private suspend fun syncAllLocked(force: Boolean, forceAssignments: Boolean): ApiResult<Unit> {
        // Each fetch is wrapped individually rather than letting the coroutineScope propagate the
        // first failure. `awaitAll` over bare `async` blocks aborts the whole scope on the first
        // throw, cancelling its siblings — so one failing resource would silently stop the others
        // being fetched *or* written, the opposite of the contract this pass has always had (see
        // SyncOrchestratorTest: one resource failing still leaves the rest synced). A resource that
        // is fresh enough to skip yields Success(null) and contributes nothing.
        val fetched: List<ApiResult<ResourceSync<*>?>> = coroutineScope {
            val srs = async { safeApiCall { subjectRepository.fetchSrsSystems(force) } }
            val subjects = async { safeApiCall { subjectRepository.fetchSubjects(force) } }
            val assignments = async { safeApiCall { assignmentRepository.fetchAssignments(force || forceAssignments) } }
            val reviewStatistics = async { safeApiCall { statsRepository.fetchReviewStatistics(force) } }
            val levelProgressions = async { safeApiCall { statsRepository.fetchLevelProgressions(force) } }
            listOf(srs, subjects, assignments, reviewStatistics, levelProgressions).awaitAll()
        }

        // Written in dependency order. SRS systems before subjects (subjects reference them), subjects
        // before assignments and statistics (both resolve subject content), and level progressions
        // last as they reference nothing.
        val writes: List<Pair<String, ResourceSync<*>>> = listOfNotNull(
            (fetched[0] as? ApiResult.Success)?.data?.let { SyncResources.SRS_SYSTEMS to it },
            (fetched[1] as? ApiResult.Success)?.data?.let { SyncResources.SUBJECTS to it },
            (fetched[2] as? ApiResult.Success)?.data?.let { SyncResources.ASSIGNMENTS to it },
            (fetched[3] as? ApiResult.Success)?.data?.let { SyncResources.REVIEW_STATISTICS to it },
            (fetched[4] as? ApiResult.Success)?.data?.let { SyncResources.LEVEL_PROGRESSIONS to it }
        )

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
    suspend fun fullRefresh(): ApiResult<Unit> = syncMutex.withLock {
        syncStateDao.clearAll()
        syncAllLocked(force = true, forceAssignments = false)
    }
}
