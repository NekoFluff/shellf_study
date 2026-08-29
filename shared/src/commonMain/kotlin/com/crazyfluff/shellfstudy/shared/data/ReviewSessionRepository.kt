package com.crazyfluff.shellfstudy.shared.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
) {
    private val store = JsonPreferenceStore(
        dataStore, json, "persisted_review_session", PersistedReviewSession.serializer()
    )

    val hasActiveSession: Flow<Boolean> = store.exists

    suspend fun save(session: PersistedReviewSession) = store.save(session)

    suspend fun load(): PersistedReviewSession? = store.load()

    suspend fun clear() = store.clear()
}
