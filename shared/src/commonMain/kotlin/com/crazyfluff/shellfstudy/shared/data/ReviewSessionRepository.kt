package com.crazyfluff.shellfstudy.shared.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.crazyfluff.shellfstudy.shared.session.PersistedSessionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PersistedReviewSession(
    val queue: List<PersistedQuestion>,
    val progress: List<PersistedItemProgress>,
    val totalQuestions: Int,
    // Active time accumulated so far, excluding any time spent away from the session (backgrounded,
    // or navigated off-screen) — see ReviewViewModel's activeElapsedMs/AppForegroundTracker. Defaults
    // to 0 for data persisted before this field existed.
    val sessionActiveElapsedMs: Long = 0L,
    // Defaults to empty for data persisted before this field existed — a resume against an older
    // snapshot just starts the "slowest answers" summary from the post-resume segment, as it always
    // used to.
    val answeredQuestions: List<PersistedAnsweredQuestion> = emptyList(),
    // Set when the current question was answered correctly but the user hasn't pressed Continue yet
    // — the WaniKani submission for it is deferred until then (see
    // ReviewViewModel.commitPendingSubmission) so undo can still retract it. Persisted so a resume
    // (process death, or navigating away and back) doesn't silently drop that submission — the grade
    // is committed as soon as the session resumes, treating that the same as an implicit Continue.
    // Defaults to null for data persisted before this field existed.
    val pendingSubmissionAssignmentId: Long? = null
)

/**
 * Persists an in-progress review session (the pending question queue + per-item progress) so it
 * survives navigating away — the review screen isn't kept alive on the back stack, so "resume"
 * means reconstructing this state rather than the ViewModel simply still being around.
 */
class ReviewSessionRepository(
    dataStore: DataStore<Preferences>,
    json: Json
) : PersistedSessionStore<PersistedReviewSession> {
    private val store = JsonPreferenceStore(
        dataStore, json, "persisted_review_session", PersistedReviewSession.serializer()
    )

    override val hasActiveSession: Flow<Boolean> = store.exists

    override suspend fun save(session: PersistedReviewSession) = store.save(session)

    /** A snapshot with an empty queue and no pending submission is a corrupted leftover, not a
     *  resumable session — see [PersistedReviewSession]'s doc comment on why this can happen (a save
     *  racing a completion-time clear). An empty queue *with* a pending submission is legitimate —
     *  it means the session reached its last question but the user hadn't tapped Continue yet, so
     *  resuming must still commit that submission — see [PersistedReviewSession.pendingSubmissionAssignmentId].
     *  Self-heals the corrupted case by clearing storage so it doesn't keep reappearing. */
    override suspend fun load(): PersistedReviewSession? {
        val loaded = store.load() ?: return null
        if (loaded.queue.isEmpty() && loaded.pendingSubmissionAssignmentId == null) {
            store.clear()
            return null
        }
        return loaded
    }

    override suspend fun clear() = store.clear()
}
