package com.crazyfluff.shellfstudy.shared.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Which phase of a lesson session [PersistedLessonSession] represents — the study flashcards
 *  ([PersistedLessonSession.studyAssignmentIds]/[PersistedLessonSession.studyIndex]) or the quiz
 *  ([PersistedLessonSession.quizQueue]/progress). Only one half of the payload is meaningful at a
 *  time, matching [com.crazyfluff.shellfstudy.shared.feature.lesson.LessonPhase]. */
enum class PersistedLessonPhase { STUDY, QUIZ }

@Serializable
data class PersistedLessonSession(
    val phase: PersistedLessonPhase = PersistedLessonPhase.QUIZ,
    val studyAssignmentIds: List<Long> = emptyList(),
    val studyIndex: Int = 0,
    val quizQueue: List<PersistedQuestion> = emptyList(),
    val progress: List<PersistedItemProgress> = emptyList(),
    val totalQuizCount: Int = 0,
    // Active time accumulated so far in the quiz phase, excluding any time spent away from the
    // session (backgrounded, or navigated off-screen) — see LessonViewModel's
    // activeElapsedMs/AppForegroundTracker. 0 means "not started yet" (a STUDY-phase snapshot, where
    // the quiz clock hasn't started) or "no value was ever persisted" (data from before this field
    // existed).
    val sessionActiveElapsedMs: Long = 0L,
    // Defaults to empty for data persisted before this field existed — a resume against an older
    // snapshot just starts the "slowest answers" summary from the post-resume segment, as it always
    // used to.
    val answeredQuestions: List<PersistedAnsweredQuestion> = emptyList()
)

/**
 * Persists an in-progress lesson session — the study flashcards once the user commits to a batch
 * via "Start session" (so backing out mid-study, or a process death, doesn't force redoing lesson
 * selection and restudying from the first card), and the quiz's pending question queue + per-item
 * progress once the quiz itself begins — so either survives navigating away. Same idea and shape as
 * [ReviewSessionRepository]. The SELECT phase alone is never persisted: nothing's been committed to
 * yet, so falling back to a fresh fetch there is harmless.
 */
class LessonSessionRepository(
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
