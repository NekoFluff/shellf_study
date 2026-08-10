package com.crazyfluff.shellfstudy.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
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
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {
    private val sessionKey = stringPreferencesKey("persisted_review_session")

    val hasActiveSession: Flow<Boolean> = dataStore.data.map { it[sessionKey] != null }

    suspend fun save(session: PersistedReviewSession) {
        dataStore.edit { prefs -> prefs[sessionKey] = json.encodeToString(session) }
    }

    suspend fun load(): PersistedReviewSession? {
        val raw = dataStore.data.first()[sessionKey] ?: return null
        return runCatching { json.decodeFromString<PersistedReviewSession>(raw) }.getOrNull()
    }

    suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(sessionKey) }
    }
}
