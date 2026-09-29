package com.crazyfluff.shellfstudy.shared.quiz

import com.crazyfluff.shellfstudy.shared.data.model.QuizDisplayItem
import com.crazyfluff.shellfstudy.shared.data.model.RankChange
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.AnswerReadingHint

/**
 * The question on screen in a lesson or review quiz — the same shape in both, embedded in each
 * feature's own quiz phase alongside whatever counts that phase keeps.
 *
 * Moving to the next question replaces this whole value, so nothing about the previous one can be
 * carried over by a field someone forgot to reset.
 */
data class QuizQuestionState<T : QuizDisplayItem>(
    val item: T,
    val type: QuestionType,
    val answerInput: String = "",
    /** How many times an answer in the wrong script was refused — see [AnswerOutcome.TypeMismatch]. */
    val answerTypeMismatchCount: Int = 0,
    val isDetailsExpanded: Boolean = false,
    /** Bumped on every undo, which returns to this same question — what the answer field keys its
     *  focus reset on, since neither [item] nor [type] changes. */
    val undoCounter: Int = 0,
    /** Bumped on every advance, even to a requeued question identical to the last — the other half of
     *  the answer field's reset key. */
    val sequence: Int = 0,
    val timing: QuizTimingUiState = QuizTimingUiState(),
    /** The grade, once the question has been answered; null while it is still being asked. */
    val grade: QuizGrade? = null
) {
    val feedback: AnswerFeedback? get() = grade?.feedback

    /** This question, answered: [grade] on screen, and its clock stopped at [elapsedMs] — the same
     *  figure the slowest-answers summary records, rather than one that ticks on through feedback. */
    fun graded(grade: QuizGrade, elapsedMs: Long): QuizQuestionState<T> = copy(
        grade = grade,
        timing = timing.copy(questionElapsedMs = elapsedMs, questionActiveSegmentStartMs = null)
    )

    /** This question, asked again after an undo: no grade, an empty field, and its clock restarted at
     *  [startedAtMs] so the retry doesn't inherit the time spent before it. */
    fun retried(startedAtMs: Long): QuizQuestionState<T> = copy(
        grade = null,
        answerInput = "",
        undoCounter = undoCounter + 1,
        timing = timing.copy(
            questionActiveElapsedMs = 0L,
            questionActiveSegmentStartMs = startedAtMs,
            questionElapsedMs = null
        )
    )
}

/**
 * What answering a question produced. Everything here exists only once there is an answer, which is
 * why it is one nullable value rather than fields that mean nothing before grading.
 */
data class QuizGrade(
    val feedback: AnswerFeedback,
    /** Whether the correct answer's text is visible. False only for a wrong answer while "require tap
     *  to reveal answer" is on for its question type, until [QuizSessionViewModel.revealAnswer]. */
    val answerRevealed: Boolean,
    /** The SRS stage change this answer causes, when it completes the item. */
    val rankChange: RankChange? = null,
    /** The reading, audio and pitch accents of a revealed reading answer, when the settings show one. */
    val answerHint: AnswerReadingHint? = null
) {
    companion object {
        /** The grade for an answer judged against [candidates], the answers it could have been. */
        fun of(
            isCorrect: Boolean,
            candidates: List<String>,
            wasCloseMatch: Boolean,
            answerRevealed: Boolean,
            rankChange: RankChange?
        ): QuizGrade = QuizGrade(
            feedback = AnswerFeedback(isCorrect, candidates.joinToString(", "), wasCloseMatch, candidates.size),
            answerRevealed = answerRevealed,
            rankChange = rankChange
        )
    }
}

/**
 * A quiz screen's state, which is a phase of some kind — loading, an error, a question, the summary —
 * only one of which asks a question. [question] is null for every other phase, which is what makes
 * "update the current question" a no-op on a screen that is not showing one.
 */
interface QuizSessionState<SELF : QuizSessionState<SELF, T>, T : QuizDisplayItem> {
    val question: QuizQuestionState<T>?

    /** This state, with [question] as its current question. */
    fun withQuestion(question: QuizQuestionState<T>): SELF
}
