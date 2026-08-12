package com.crazyfluff.shellfstudy.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class PersistedQuestion(val assignmentId: Long, val questionType: String)

@Serializable
data class PersistedItemProgress(
    val assignmentId: Long,
    val meaningDone: Boolean,
    val readingDone: Boolean,
    val hadIncorrectMeaning: Boolean,
    val hadIncorrectReading: Boolean
)

@Serializable
data class PersistedReviewSession(
    val queue: List<PersistedQuestion>,
    val progress: List<PersistedItemProgress>,
    val totalQuestions: Int
)

/**
 * Persists an in-progress review session (the pending question queue + per-item progress) so it
 * survives navigating away — the review screen isn't kept alive on the back stack, so "resume"
 * means reconstructing this state rather than the ViewModel simply still being around.
 */
@Singleton
class ReviewSessionRepository @Inject constructor(
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
