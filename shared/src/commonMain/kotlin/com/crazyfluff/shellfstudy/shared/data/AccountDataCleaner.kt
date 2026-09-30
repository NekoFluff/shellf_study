package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.database.AssignmentDao
import com.crazyfluff.shellfstudy.shared.database.LevelProgressionDao
import com.crazyfluff.shellfstudy.shared.database.ReviewStatisticDao
import com.crazyfluff.shellfstudy.shared.database.SyncStateDao
import com.crazyfluff.shellfstudy.shared.database.outbox.OutboxDao
import com.crazyfluff.shellfstudy.shared.session.LessonSessionController
import com.crazyfluff.shellfstudy.shared.session.ReviewSessionController
import kotlinx.coroutines.CancellationException

/**
 * The account-scoped stores [AccountDataCleaner] wipes. Named so a partial wipe can say *which* ones
 * didn't clear — the previous shape swallowed each failure into `runCatching { … }` and returned
 * `Unit`, so a wipe that cleared nothing looked exactly like one that cleared everything.
 */
enum class AccountStore {
    Assignments,
    ReviewStatistics,
    LevelProgressions,
    SyncState,
    PendingReviews,
    PendingLessonStarts,
    OutboxAuthBlock,
    DashboardCache,
    LastSessionSummary,
    ReviewSession,
    LessonSession
}

/** What a wipe managed to do — see [AccountDataCleaner.clearAll]. */
sealed interface AccountCleanupOutcome {
    data object Complete : AccountCleanupOutcome

    /**
     * These stores did not clear. Every *other* store did: one failure must not block the rest, and a
     * partial wipe still leaves the next login better off than no wipe at all. The causes are kept
     * rather than dropped, since this module has no logging sink of its own and a caller that wants to
     * record them is the only place they can surface.
     */
    data class Partial(val failures: Map<AccountStore, Throwable>) : AccountCleanupOutcome
}

/**
 * Wipes every store that's scoped to the logged-in WaniKani account, so a new login can't be
 * clobbered by data left behind from whoever was logged in before. Deliberately leaves untouched
 * anything that isn't account-scoped: [FriendRepository]/friend stats (hand-added external
 * tokens meant to survive an account switch), subjects/SRS systems (shared WaniKani content,
 * identical for every account), [SettingsRepository] (device/UI prefs), and the study-day and
 * study-time history. That history can't be re-fetched, so it outlives logout and is cleared only
 * when a different account signs in; see [LocalHistoryGuard].
 *
 * Each step is independent — one store failing to clear must not block the rest, and must not leave
 * [LogoutCoordinator.logout] in a worse partial state than before this existed. What each failure
 * *does* do is show up in the returned [AccountCleanupOutcome] instead of vanishing.
 */
class AccountDataCleaner(
    private val assignmentDao: AssignmentDao,
    private val reviewStatisticDao: ReviewStatisticDao,
    private val levelProgressionDao: LevelProgressionDao,
    private val syncStateDao: SyncStateDao,
    private val outboxDao: OutboxDao,
    private val outboxRepository: OutboxRepository,
    private val dashboardCacheRepository: DashboardCacheRepository,
    private val lastSessionSummaryRepository: LastSessionSummaryRepository,
    private val reviewSessionController: ReviewSessionController,
    private val lessonSessionController: LessonSessionController
) {
    suspend fun clearAll(): AccountCleanupOutcome {
        val failures = mutableMapOf<AccountStore, Throwable>()

        wipe(AccountStore.Assignments, failures) { assignmentDao.clearAll() }
        wipe(AccountStore.ReviewStatistics, failures) { reviewStatisticDao.clearAll() }
        wipe(AccountStore.LevelProgressions, failures) { levelProgressionDao.clearAll() }
        wipe(AccountStore.SyncState, failures) { syncStateDao.clearAll() }
        wipe(AccountStore.PendingReviews, failures) { outboxDao.clearReviewSubmissions() }
        wipe(AccountStore.PendingLessonStarts, failures) { outboxDao.clearLessonStarts() }
        wipe(AccountStore.OutboxAuthBlock, failures) { outboxRepository.resetAuthBlock() }
        wipe(AccountStore.DashboardCache, failures) { dashboardCacheRepository.clear() }
        wipe(AccountStore.LastSessionSummary, failures) { lastSessionSummaryRepository.clearAll() }
        wipe(AccountStore.ReviewSession, failures) { reviewSessionController.abandon() }
        wipe(AccountStore.LessonSession, failures) { lessonSessionController.abandon() }

        return if (failures.isEmpty()) AccountCleanupOutcome.Complete else AccountCleanupOutcome.Partial(failures)
    }

    /**
     * Runs one store's clear, recording the failure under [store]. Cancellation is rethrown rather than
     * recorded: these are `suspend` calls, and catching [CancellationException] here would let a
     * cancelled logout march through every remaining store and then report success — the caller would
     * resume as if it had finished. `runCatching` did exactly that, since it catches cancellation too.
     */
    private suspend fun wipe(
        store: AccountStore,
        failures: MutableMap<AccountStore, Throwable>,
        clear: suspend () -> Unit
    ) {
        try {
            clear()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failures[store] = e
        }
    }
}
