package com.crazyfluff.shellfstudy.shared.data

import kotlinx.serialization.Serializable

/*
 * The persisted building blocks shared by an in-progress lesson session and an in-progress review
 * session ([PersistedLessonSession] / [PersistedReviewSession]). Both features run the same quiz
 * engine over the same per-item progress and per-answer shapes, so these live here rather than being
 * declared twice — they were previously mirrored, byte-for-byte, in each session repository.
 *
 * The two sessions still persist under separate DataStore keys, since abandoning or clearing one must
 * not disturb the other and the dashboard signals an active lesson and an active review separately.
 */

/** One still-unanswered question in a persisted queue. */
@Serializable
data class PersistedQuestion(val assignmentId: Long, val questionType: String)

/** Per-item progress within a session — which halves are done and which were ever answered wrong. */
@Serializable
data class PersistedItemProgress(
    val assignmentId: Long,
    val meaningDone: Boolean,
    val readingDone: Boolean,
    val hadIncorrectMeaning: Boolean,
    val hadIncorrectReading: Boolean
)

/** One graded answer, for rebuilding the "slowest answers" summary across a pause/resume — see
 *  [com.crazyfluff.shellfstudy.shared.quiz.AnsweredQuestionRecord], the in-memory shape this mirrors. */
@Serializable
data class PersistedAnsweredQuestion(
    val assignmentId: Long,
    val questionType: String,
    val isCorrect: Boolean,
    val elapsedMs: Long
)
