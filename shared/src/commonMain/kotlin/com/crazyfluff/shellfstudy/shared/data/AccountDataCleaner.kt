package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.database.AssignmentDao
import com.crazyfluff.shellfstudy.shared.database.LevelProgressionDao
import com.crazyfluff.shellfstudy.shared.database.ReviewStatisticDao
import com.crazyfluff.shellfstudy.shared.database.SyncStateDao
import com.crazyfluff.shellfstudy.shared.database.outbox.OutboxDao
import com.crazyfluff.shellfstudy.shared.database.studyactivity.StudyActivityDao

/**
 * Wipes every store that's scoped to the logged-in WaniKani account, so a new login can't be
 * clobbered by data left behind from whoever was logged in before. Deliberately leaves untouched
 * anything that isn't account-scoped: [FriendRepository]/friend stats (hand-added external
 * tokens meant to survive an account switch), subjects/SRS systems (shared WaniKani content,
 * identical for every account), and [SettingsRepository] (device/UI prefs).
 *
 * Each step is independent — one store failing to clear must not block the rest, and must not
 * leave [LogoutCoordinator.logout] in a worse partial state than before this existed.
 */
class AccountDataCleaner(
    private val assignmentDao: AssignmentDao,
    private val reviewStatisticDao: ReviewStatisticDao,
    private val levelProgressionDao: LevelProgressionDao,
    private val syncStateDao: SyncStateDao,
    private val outboxDao: OutboxDao,
    private val studyActivityDao: StudyActivityDao,
    private val outboxRepository: OutboxRepository,
    private val dashboardCacheRepository: DashboardCacheRepository,
    private val lastSessionSummaryRepository: LastSessionSummaryRepository,
    private val reviewSessionRepository: ReviewSessionRepository,
    private val lessonSessionRepository: LessonSessionRepository
) {
    suspend fun clearAll() {
        runCatching { assignmentDao.clearAll() }
        runCatching { reviewStatisticDao.clearAll() }
        runCatching { levelProgressionDao.clearAll() }
        runCatching { syncStateDao.clearAll() }
        runCatching { outboxDao.clearReviewSubmissions() }
        runCatching { outboxDao.clearLessonStarts() }
        runCatching { studyActivityDao.clearAll() }
        runCatching { outboxRepository.resetAuthBlock() }
        runCatching { dashboardCacheRepository.clear() }
        runCatching { lastSessionSummaryRepository.clear() }
        runCatching { reviewSessionRepository.clear() }
        runCatching { lessonSessionRepository.clear() }
    }
}
