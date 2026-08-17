package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.DashboardSummary
import com.crazyfluff.shellfstudy.shared.data.model.WaniKaniUser
import com.crazyfluff.shellfstudy.shared.sync.SyncOrchestrator
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

    suspend fun fetchUserAndSummary(): Pair<ApiResult<WaniKaniUser>, ApiResult<DashboardSummary>> {
        val userResult = waniKaniRepository.fetchUser()
        val summaryResult = waniKaniRepository.fetchDashboardSummary()
        return userResult to summaryResult
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
