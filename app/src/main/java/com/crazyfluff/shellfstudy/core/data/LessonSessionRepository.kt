package com.crazyfluff.shellfstudy.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class PersistedLessonQuestion(val assignmentId: Long, val questionType: String)

@Serializable
data class PersistedLessonSession(
    val quizQueue: List<PersistedLessonQuestion>,
    val totalQuizCount: Int
)

/**
 * Persists an in-progress lesson quiz (the pending question queue) so it survives navigating away
 * — same idea and shape as [ReviewSessionRepository], which the lesson quiz previously had no
 * equivalent of, so a mid-quiz app-process death or back-navigation silently lost all progress.
 * Only the quiz phase is persisted: the SELECT/STUDY phases have nothing graded yet to lose, so
 * falling back to a fresh fetch there is harmless.
 */
@Singleton
class LessonSessionRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {
    private val sessionKey = stringPreferencesKey("persisted_lesson_session")

    suspend fun save(session: PersistedLessonSession) {
        dataStore.edit { prefs -> prefs[sessionKey] = json.encodeToString(session) }
    }

    suspend fun load(): PersistedLessonSession? {
        val raw = dataStore.data.first()[sessionKey] ?: return null
        return runCatching { json.decodeFromString<PersistedLessonSession>(raw) }.getOrNull()
    }

    suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(sessionKey) }
    }
}
