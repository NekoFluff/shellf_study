package com.crazyfluff.shellfstudy.shared.sync

import com.crazyfluff.shellfstudy.shared.data.ApiResult
import com.crazyfluff.shellfstudy.shared.data.AssignmentRepository
import com.crazyfluff.shellfstudy.shared.data.StatsRepository
import com.crazyfluff.shellfstudy.shared.data.SubjectRepository
import com.crazyfluff.shellfstudy.shared.database.SyncStateDao
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The single place that sequences a full sync pass across every repository — both the periodic
 * `SyncWorker` and manual triggers (app open, pull-to-refresh, and [fullRefresh]'s cursor reset)
 * call this instead of duplicating the ordering themselves.
 */
class SyncOrchestrator(
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

    suspend fun syncAll(force: Boolean = false): ApiResult<Unit> = syncMutex.withLock { syncAllLocked(force) }

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
     * the first — and each rewrite is its own Room write transaction and therefore its own invalidation
     * broadcast to every observable assignments query. Forcing the resource *within* the pass still
     * reuses its saved `updated_after` cursor, so this stays an incremental fetch, not a full resync.
     */
    suspend fun syncAllForcingAssignments(): ApiResult<Unit> = syncMutex.withLock {
        syncAllLocked(force = false, forceAssignments = true)
    }

    private suspend fun syncAllLocked(force: Boolean, forceAssignments: Boolean = false): ApiResult<Unit> = coroutineScope {
        // SRS systems and subjects first — everything else references subject IDs, and subjects
        // reference spaced_repetition_system_id.
        val srsResult = subjectRepository.syncSrsSystems(force)
        val subjectsResult = subjectRepository.syncSubjects(force)

        val assignmentsDeferred = async { assignmentRepository.syncAssignments(force || forceAssignments) }
        val reviewStatisticsDeferred = async { statsRepository.syncReviewStatistics(force) }
        val levelProgressionsDeferred = async { statsRepository.syncLevelProgressions(force) }

        val results = listOf(
            srsResult,
            subjectsResult,
            assignmentsDeferred.await(),
            reviewStatisticsDeferred.await(),
            levelProgressionsDeferred.await()
        )
        results.filterIsInstance<ApiResult.Error>().firstOrNull() ?: ApiResult.Success(Unit)
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
        syncAllLocked(force = true)
    }
}
