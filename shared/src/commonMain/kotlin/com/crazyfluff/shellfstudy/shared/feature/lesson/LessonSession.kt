package com.crazyfluff.shellfstudy.shared.feature.lesson

import com.crazyfluff.shellfstudy.shared.data.DEFAULT_LESSON_BATCH_SIZE
import com.crazyfluff.shellfstudy.shared.data.PersistedLessonPhase
import com.crazyfluff.shellfstudy.shared.data.PersistedLessonSession
import com.crazyfluff.shellfstudy.shared.data.model.LessonItem
import com.crazyfluff.shellfstudy.shared.data.model.LevelUpProgress
import com.crazyfluff.shellfstudy.shared.quiz.QuizSession

/**
 * A lesson session the learner has committed to: its frozen plan, where in it they are, and the quiz
 * state that spans every batch.
 *
 * The plan is held as ids ([plan], sliced into batches by [batchSize] — see [LessonSessionPlanner]) so
 * it persists and restores verbatim, and is resolved through [itemsById] rather than through the
 * lesson queue's due filter: an item leaves that filter the moment its lesson completes, but stays part
 * of this session's tally.
 *
 * [quiz] carries one batch's questions at a time, while its progress and answers accumulate across the
 * whole session, which is what the summary at the end covers.
 */
internal data class LessonSession(
    val plan: List<Long> = emptyList(),
    val batchSize: Int = DEFAULT_LESSON_BATCH_SIZE,
    val itemsById: Map<Long, LessonItem> = emptyMap(),
    val batchIndex: Int = 0,
    val quiz: QuizSession<LessonItem> = QuizSession(),
    /** Items whose lesson has been marked started this session — the guard that submits each once. */
    val startedAssignmentIds: Set<Long> = emptySet()
) {
    val batches: List<List<Long>> get() = LessonSessionPlanner.batches(plan, batchSize)
    val batchCount: Int get() = batches.size

    /** Whether the batch being quizzed is the session's last, so its end is the summary. */
    val isOnLastBatch: Boolean get() = batchIndex + 1 >= batchCount

    /** The resolvable items of batch [index], in plan order. An id the cache no longer has is dropped —
     *  a resume rejects a plan with holes in it up front, so in practice this only filters an item
     *  deleted between batches. */
    fun batchItems(index: Int): List<LessonItem> = batches.getOrNull(index).orEmpty().mapNotNull { itemsById[it] }

    /** Enough to resume mid-flashcard-study: which batch, and which card of it. */
    fun studySnapshot(studyIndex: Int, sessionActiveElapsedMs: Long): PersistedLessonSession = PersistedLessonSession(
        phase = PersistedLessonPhase.STUDY,
        sessionAssignmentIds = plan,
        batchSize = batchSize,
        batchIndex = batchIndex,
        studyIndex = studyIndex,
        sessionActiveElapsedMs = sessionActiveElapsedMs
    )

    /**
     * The quiz in progress. A quiz whose questions have all been answered has, by definition, just
     * finished its pass — last answer graded, checkpoint next — so it is recorded as that checkpoint
     * rather than as a quiz with nothing left to ask, which the repository would rightly treat as a
     * corrupted leftover and clear.
     */
    fun quizSnapshot(sessionActiveElapsedMs: Long): PersistedLessonSession =
        if (quiz.isEmpty) {
            checkpointSnapshot(sessionActiveElapsedMs)
        } else {
            PersistedLessonSession(
                phase = PersistedLessonPhase.QUIZ,
                sessionAssignmentIds = plan,
                batchSize = batchSize,
                batchIndex = batchIndex,
                quizQueue = quiz.persistedInFlight(),
                progress = quiz.persistedProgress(),
                totalQuizCount = quiz.totalQuestions,
                sessionActiveElapsedMs = sessionActiveElapsedMs,
                answeredQuestions = quiz.persistedAnswers()
            )
        }

    /** The checkpoint at the end of a pass: every batch up to [batchIndex] is done, so the snapshot
     *  records the *next* batch as where a resume belongs (see [PersistedLessonSession.batchIndex]). */
    fun checkpointSnapshot(sessionActiveElapsedMs: Long): PersistedLessonSession = PersistedLessonSession(
        phase = PersistedLessonPhase.CHECKPOINT,
        sessionAssignmentIds = plan,
        batchSize = batchSize,
        batchIndex = batchIndex + 1,
        progress = quiz.persistedProgress(),
        totalQuizCount = quiz.totalQuestions,
        sessionActiveElapsedMs = sessionActiveElapsedMs,
        answeredQuestions = quiz.persistedAnswers()
    )
}

/** What the lesson picker re-sorts without another fetch: the queue Room handed back, plus the level-up
 *  context [LessonPrioritizer] needs. Only read while a Select phase is showing. */
internal data class LessonPicker(
    val queue: List<LessonItem>,
    val levelUpProgress: LevelUpProgress,
    val isStrained: Boolean
) {
    fun sorted(sort: LessonSort): List<LessonItem> = LessonPrioritizer.prioritize(
        items = queue,
        levelUpProgress = levelUpProgress,
        isStrained = isStrained,
        sort = sort
    )
}
