package com.crazyfluff.shellfstudy.shared.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.crazyfluff.shellfstudy.shared.session.PersistedSessionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Which phase of a lesson session [PersistedLessonSession] represents — the study flashcards
 *  ([PersistedLessonSession.studyIndex], over the batch [PersistedLessonSession.batchIndex] names),
 *  the quiz itself ([PersistedLessonSession.quizQueue]/progress), or a batch checkpoint the learner
 *  parked at (every batch up to [PersistedLessonSession.batchIndex] is done). Mirrors
 *  [com.crazyfluff.shellfstudy.shared.feature.lesson.LessonUiState.Phase], so only the fields that
 *  phase actually uses are meaningful at a time. */
enum class PersistedLessonPhase { STUDY, QUIZ, CHECKPOINT }

/** Which pass over the queue a QUIZ-phase snapshot belongs to — the lesson's own quiz for a batch,
 *  or the optional end-of-session extra pass over the items missed during it. Unlike the lesson quiz,
 *  a cleanup pass can never change the session summary (those items' misses are already recorded), so
 *  resuming one has to know which of the two it is restoring. */
enum class PersistedQuizRound { LESSON, CLEANUP }

/**
 * A lesson session's persisted snapshot.
 *
 * The session's shape is carried by a *plan* rather than by the current batch alone:
 * [sessionAssignmentIds] is every assignment id the learner committed to at "Start session", in
 * study order, and batches are those ids re-sliced by [batchSize] (see
 * [com.crazyfluff.shellfstudy.shared.feature.lesson.LessonSessionPlanner]). Persisting the plan — not
 * the batch boundaries — is what keeps a resumed session on the same batches, in the same order, and
 * is why [batchIndex] alone is enough to say where the learner was.
 */
@Serializable
data class PersistedLessonSession(
    val phase: PersistedLessonPhase = PersistedLessonPhase.QUIZ,
    /** The frozen plan: every assignment id this session committed to, in study order. Empty only in
     *  snapshots written before session plans existed — [LessonSessionRepository.load] migrates those
     *  in place rather than discarding the session. */
    val sessionAssignmentIds: List<Long> = emptyList(),
    val batchSize: Int = DEFAULT_LESSON_BATCH_SIZE,
    /** Which batch the snapshot refers to, 0-based. For [PersistedLessonPhase.CHECKPOINT] this is the
     *  *next* batch to study, so it equals the batch count exactly when every batch is done — the same
     *  convention [com.crazyfluff.shellfstudy.shared.feature.lesson.LessonSessionPlanner.batches]'s
     *  empty-selection rule relies on. */
    val batchIndex: Int = 0,
    val quizRound: PersistedQuizRound = PersistedQuizRound.LESSON,
    /** Read only to migrate a snapshot written before session plans existed, where a STUDY payload's
     *  selected batch *was* the whole session — see [migratedFromLegacyShape]. Never written by this
     *  version (a migrated snapshot re-saves its plan through [sessionAssignmentIds]). */
    val studyAssignmentIds: List<Long> = emptyList(),
    val studyIndex: Int = 0,
    val quizQueue: List<PersistedQuestion> = emptyList(),
    val progress: List<PersistedItemProgress> = emptyList(),
    val totalQuizCount: Int = 0,
    // Active time accumulated so far in the session, excluding any time spent away from the session
    // (backgrounded, navigated off-screen, or sitting on a batch checkpoint) — see LessonViewModel's
    // sessionTiming/AppForegroundTracker. 0 means "not started yet" (a STUDY-phase snapshot, where the
    // session clock hasn't started) or "no value was ever persisted" (data from before this field
    // existed).
    val sessionActiveElapsedMs: Long = 0L,
    // Defaults to empty for data persisted before this field existed — a resume against an older
    // snapshot just starts the "slowest answers" summary from the post-resume segment, as it always
    // used to.
    val answeredQuestions: List<PersistedAnsweredQuestion> = emptyList()
) {
    /** Fills in a plan for a snapshot written before plans existed, so upgrading mid-session keeps the
     *  session rather than silently dropping it.
     *
     *  Both pre-plan shapes describe exactly one batch: a STUDY snapshot held the selected batch's ids,
     *  and a QUIZ snapshot held only the queue/progress for the batch being quizzed. Reconstructing the
     *  plan as "those ids, in one batch" therefore reproduces the session the old build would have
     *  resumed, and the plan then takes over from the first save onward. A QUIZ snapshot's ids come from
     *  the shuffled queue, so their order is arbitrary — harmless here precisely because they form a
     *  single batch, so nothing downstream slices them. */
    fun migratedFromLegacyShape(): PersistedLessonSession {
        if (sessionAssignmentIds.isNotEmpty()) return this
        val legacyIds = when (phase) {
            PersistedLessonPhase.STUDY -> studyAssignmentIds
            PersistedLessonPhase.QUIZ -> (quizQueue.map { it.assignmentId } + progress.map { it.assignmentId }).distinct()
            PersistedLessonPhase.CHECKPOINT -> emptyList()
        }
        if (legacyIds.isEmpty()) return this
        return copy(sessionAssignmentIds = legacyIds, batchSize = legacyIds.size, quizRound = PersistedQuizRound.LESSON)
    }
}

/**
 * Persists an in-progress lesson session — its frozen plan, the study flashcards of whichever batch
 * the learner is on (so backing out mid-study, or a process death, doesn't force redoing lesson
 * selection and restudying from the first card), the quiz's pending question queue + per-item progress
 * once a batch's quiz begins, and the checkpoint a learner parked at between batches. Same idea and
 * shape as [ReviewSessionRepository]. A SELECT-phase session is never persisted: nothing's been
 * committed to yet, so falling back to a fresh fetch there is harmless.
 */
class LessonSessionRepository(
    dataStore: DataStore<Preferences>,
    json: Json
) : PersistedSessionStore<PersistedLessonSession> {
    private val store = JsonPreferenceStore(
        dataStore, json, "persisted_lesson_session", PersistedLessonSession.serializer()
    )

    override val hasActiveSession: Flow<Boolean> = store.exists

    override suspend fun save(session: PersistedLessonSession) = store.save(session)

    /** A snapshot is resumable when it still describes a session with items in it — a plan, and (for a
     *  QUIZ snapshot) a queue to work through. A CHECKPOINT snapshot is deliberately resumable with an
     *  empty queue: that's the normal shape of a learner who parked between batches, or finished the
     *  last batch and backed out before the summary. Anything less (no plan at all, or a quiz with
     *  nothing left to answer) is a corrupted leftover rather than a resumable session, so it self-heals
     *  by clearing storage instead of reappearing forever. */
    override suspend fun load(): PersistedLessonSession? {
        val loaded = store.load()?.migratedFromLegacyShape() ?: return null
        val isResumable = loaded.sessionAssignmentIds.isNotEmpty() &&
            (loaded.phase != PersistedLessonPhase.QUIZ || loaded.quizQueue.isNotEmpty())
        if (!isResumable) {
            store.clear()
            return null
        }
        return loaded
    }

    override suspend fun clear() = store.clear()
}
