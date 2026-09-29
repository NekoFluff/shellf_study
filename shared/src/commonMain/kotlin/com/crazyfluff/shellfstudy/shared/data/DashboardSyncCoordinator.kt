package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.DashboardSummary
import com.crazyfluff.shellfstudy.shared.data.model.WaniKaniUser
import com.crazyfluff.shellfstudy.shared.sync.SyncOrchestrator
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

/** Bundles the network calls and cache write behind a dashboard refresh. Deliberately excludes any
 *  error-handling/state-update decisions — those differ between a forced refresh and a background
 *  resume, so callers keep that branching themselves. */
class DashboardSyncCoordinator(
    private val waniKaniRepository: WaniKaniRepository,
    private val syncOrchestrator: SyncOrchestrator,
    private val dashboardCacheRepository: DashboardCacheRepository
) {
    val cachedSummary: Flow<CachedDashboardSummary?> = dashboardCacheRepository.cachedSummary

    suspend fun sync(force: Boolean): ApiResult<Unit> = syncOrchestrator.syncAll(force)

    /** [sync] with assignments forced but the other resources left staleness-gated — the resume path's
     *  combination. See [SyncOrchestrator.syncAllForcingAssignments]. */
    suspend fun syncForcingAssignments(): ApiResult<Unit> = syncOrchestrator.syncAllForcingAssignments()

    /**
     * `/user` and `/summary` in parallel. They are independent endpoints with no ordering requirement
     * between them, and together they are the last thing on the dashboard's critical path — its
     * fetchState only leaves `InFlight` once both have landed. Awaiting them one after the other made
     * a dashboard load cost two serialized round trips where one would do.
     */
    suspend fun fetchUserAndSummary(): Pair<ApiResult<WaniKaniUser>, ApiResult<DashboardSummary>> =
        coroutineScope {
            val userDeferred = async { waniKaniRepository.fetchUser() }
            val summaryDeferred = async { waniKaniRepository.fetchDashboardSummary() }
            userDeferred.await() to summaryDeferred.await()
        }

    suspend fun cacheSummary(user: WaniKaniUser, summary: DashboardSummary, syncedAtMillis: Long) {
        dashboardCacheRepository.save(
            username = user.username,
            level = user.level,
            lessonCount = summary.lessonCount,
            reviewCount = summary.reviewCount,
            syncedAtMillis = syncedAtMillis
        )
    }
}
