package com.crazyfluff.shellfstudy.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class PersistedLessonQuestion(val assignmentId: Long, val questionType: String)

@Serializable
data class PersistedLessonItemProgress(
    val assignmentId: Long,
    val meaningDone: Boolean,
    val readingDone: Boolean,
    val hadIncorrectMeaning: Boolean,
    val hadIncorrectReading: Boolean
)

@Serializable
data class PersistedLessonSession(
    val quizQueue: List<PersistedLessonQuestion>,
    val progress: List<PersistedLessonItemProgress> = emptyList(),
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
    dataStore: DataStore<Preferences>,
    json: Json
) {
    private val store = JsonPreferenceStore(
        dataStore, json, "persisted_lesson_session", PersistedLessonSession.serializer()
    )

    val hasActiveSession: Flow<Boolean> = store.exists

    suspend fun save(session: PersistedLessonSession) = store.save(session)

    suspend fun load(): PersistedLessonSession? = store.load()

    suspend fun clear() = store.clear()
}
