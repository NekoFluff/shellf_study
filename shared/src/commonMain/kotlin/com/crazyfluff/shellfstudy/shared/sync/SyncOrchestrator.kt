package com.crazyfluff.shellfstudy.shared.sync

import com.crazyfluff.shellfstudy.shared.data.ApiResult
import com.crazyfluff.shellfstudy.shared.data.AssignmentRepository
import com.crazyfluff.shellfstudy.shared.data.StatsRepository
import com.crazyfluff.shellfstudy.shared.data.SubjectRepository
import com.crazyfluff.shellfstudy.shared.database.SyncStateDao
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async

/**
 * The single place that sequences a full sync pass across every repository — both the periodic
 * `SyncWorker` and manual triggers (app open, pull-to-refresh) call this instead of duplicating
 * the ordering themselves.
 */
class SyncOrchestrator(
    private val subjectRepository: SubjectRepository,
    private val assignmentRepository: AssignmentRepository,
    private val statsRepository: StatsRepository,
    private val syncStateDao: SyncStateDao
) {
    suspend fun syncAll(force: Boolean = false): ApiResult<Unit> = coroutineScope {
        // SRS systems and subjects first — everything else references subject IDs, and subjects
        // reference spaced_repetition_system_id.
        val srsResult = subjectRepository.syncSrsSystems(force)
        val subjectsResult = subjectRepository.syncSubjects(force)

        val assignmentsDeferred = async { assignmentRepository.syncAssignments(force) }
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
    suspend fun fullRefresh(): ApiResult<Unit> {
        syncStateDao.clearAll()
        return syncAll(force = true)
    }
}
