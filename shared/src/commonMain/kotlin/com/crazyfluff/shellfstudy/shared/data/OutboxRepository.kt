package com.crazyfluff.shellfstudy.shared.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.crazyfluff.shellfstudy.shared.data.model.ReviewGrade
import com.crazyfluff.shellfstudy.shared.database.outbox.OutboxDao
import com.crazyfluff.shellfstudy.shared.database.outbox.PendingLessonStartEntity
import com.crazyfluff.shellfstudy.shared.database.outbox.PendingReviewSubmissionEntity
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

private val BLOCKED_ON_AUTH_KEY = booleanPreferencesKey("outbox_blocked_on_auth")

/**
 * The single place "user just graded a review / started a lesson" turns into a durable local
 * write plus a background sync request — the only thing the review/lesson ViewModels talk to
 * directly. Never touches the network itself; that's the outbox sync worker's job.
 */
class OutboxRepository(
    private val outboxDao: OutboxDao,
    private val outboxSyncScheduler: OutboxSyncScheduler,
    private val dataStore: DataStore<Preferences>
) {
    suspend fun enqueueReviewSubmission(assignmentId: Long, subjectId: Long, grade: ReviewGrade) {
        outboxDao.insertReviewSubmission(
            PendingReviewSubmissionEntity(
                assignmentId = assignmentId,
                subjectId = subjectId,
                // The real wrong-answer counts, not a flattened 0/1 — WaniKani's demotion rule is
                // ceil(incorrect / 2) * penalty, so how many times a question was missed changes the
                // ending stage. See ReviewGrade and SrsStageCalculator.nextStageOnIncorrect.
                incorrectMeaningAnswers = grade.incorrectMeaning,
                incorrectReadingAnswers = grade.incorrectReading,
                gradedAt = Clock.System.now().toString()
            )
        )
        outboxSyncScheduler.requestSync()
    }

    suspend fun enqueueLessonStart(assignmentId: Long, subjectId: Long) {
        outboxDao.insertLessonStart(
            PendingLessonStartEntity(assignmentId = assignmentId, subjectId = subjectId, startedAt = Clock.System.now().toString())
        )
        outboxSyncScheduler.requestSync()
    }

    /** Bypasses the debounce on [OutboxSyncScheduler.requestSync] — call when the caller knows the
     *  user is done (session completion, dashboard resumption) and wants pending items to clear
     *  promptly instead of waiting out the coalescing delay. */
    fun requestSyncNow() {
        outboxSyncScheduler.requestImmediateSync()
    }

    fun observePendingCount(): Flow<Int> =
        combine(outboxDao.observePendingReviewSubmissionCount(), outboxDao.observePendingLessonStartCount()) { reviews, lessons ->
            reviews + lessons
        }

    /** True once the sync worker has seen a confirmed 401 — pending rows are left untouched, this
     *  just signals the UI that sync is paused until re-auth (see DashboardViewModel's own 401
     *  handling, which is what actually logs the user out). distinctUntilChanged() because
     *  dataStore is shared app-wide, so this would otherwise re-emit on every unrelated write. */
    val blockedOnAuth: Flow<Boolean> = dataStore.data.map { it[BLOCKED_ON_AUTH_KEY] ?: false }.distinctUntilChanged()

    suspend fun setBlockedOnAuth(blocked: Boolean) {
        dataStore.edit { prefs -> prefs[BLOCKED_ON_AUTH_KEY] = blocked }
    }

    suspend fun resetAuthBlock() = setBlockedOnAuth(false)

    private val _workDelivered =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Fires after a drain pass that got at least one queued grade or lesson start to WaniKani —
     *  typically work done offline, reaching the server once the connection is back. Anything that
     *  shows server-derived counts should refetch then: a `/summary` read before the drain still
     *  counts that work as outstanding. */
    val workDelivered: SharedFlow<Unit> = _workDelivered.asSharedFlow()

    internal fun notifyWorkDelivered() {
        _workDelivered.tryEmit(Unit)
    }
}
