package com.crazyfluff.shellfstudy.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.crazyfluff.shellfstudy.core.data.model.ReviewGrade
import com.crazyfluff.shellfstudy.shared.database.outbox.OutboxDao
import com.crazyfluff.shellfstudy.shared.database.outbox.PendingLessonStartEntity
import com.crazyfluff.shellfstudy.shared.database.outbox.PendingReviewSubmissionEntity
import com.crazyfluff.shellfstudy.core.sync.OutboxSyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val BLOCKED_ON_AUTH_KEY = booleanPreferencesKey("outbox_blocked_on_auth")

/**
 * The single place "user just graded a review / started a lesson" turns into a durable local
 * write plus a background sync request — the only thing the review/lesson ViewModels talk to
 * directly. Never touches the network itself; that's [com.crazyfluff.shellfstudy.core.sync.OutboxSyncWorker]'s job.
 */
@Singleton
class OutboxRepository @Inject constructor(
    private val outboxDao: OutboxDao,
    private val outboxSyncScheduler: OutboxSyncScheduler,
    private val dataStore: DataStore<Preferences>
) {
    suspend fun enqueueReviewSubmission(assignmentId: Long, subjectId: Long, grade: ReviewGrade) {
        outboxDao.insertReviewSubmission(
            PendingReviewSubmissionEntity(
                assignmentId = assignmentId,
                subjectId = subjectId,
                incorrectMeaningAnswers = if (grade.meaningCorrect) 0 else 1,
                incorrectReadingAnswers = if (grade.readingCorrect) 0 else 1,
                gradedAt = Instant.now().toString()
            )
        )
        outboxSyncScheduler.requestSync()
    }

    suspend fun enqueueLessonStart(assignmentId: Long, subjectId: Long) {
        outboxDao.insertLessonStart(
            PendingLessonStartEntity(assignmentId = assignmentId, subjectId = subjectId, startedAt = Instant.now().toString())
        )
        outboxSyncScheduler.requestSync()
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
}
