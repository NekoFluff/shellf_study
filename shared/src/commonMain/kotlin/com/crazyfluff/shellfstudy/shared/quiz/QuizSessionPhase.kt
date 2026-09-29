package com.crazyfluff.shellfstudy.shared.quiz

import com.crazyfluff.shellfstudy.shared.data.model.QuizDisplayItem

/**
 * The part of a quiz screen's state that one question lives in — Lesson's `Phase.Quiz` and Review's
 * `Phase.Active`, which carry the same question fields under different phase names.
 *
 * This exists so the flow that grades, reveals and undoes an answer can be written once. That flow was
 * implemented twice, in `LessonViewModel` and `ReviewViewModel`, with the only differences being which
 * phase the state was cast to and which `update…` helper wrote it back — transcription, not policy —
 * and the two copies had to be kept in step by hand.
 *
 * Only the members the shared flow actually reads or writes are here. A phase keeps the fields that
 * are its own business: the lesson's batch counters, the review's in-flight counts and wrap-up flag.
 *
 * [SELF] is the implementing type, so the `with…` functions return the concrete phase rather than the
 * interface — the shared code writes a phase back into its state, and the state's own type has to be
 * preserved to do that.
 */
interface QuizSessionPhase<T : QuizDisplayItem, SELF : QuizSessionPhase<T, SELF>> {
    val currentItem: T
    val currentQuestionType: QuestionType
    val answerInput: String
    val feedback: AnswerFeedback?
    val answerRevealed: Boolean
    val isDetailsExpanded: Boolean
    val answerTypeMismatchCount: Int
    val timing: QuizTimingUiState

    fun withAnswerInput(value: String): SELF
    fun withAnswerRevealed(revealed: Boolean): SELF
    fun withDetailsExpanded(expanded: Boolean): SELF
    fun withAnswerTypeMismatchCount(count: Int): SELF
    fun withTiming(timing: QuizTimingUiState): SELF
}

/**
 * A quiz screen's state, which is a phase of some kind — Loading, Error, a question, the summary —
 * only one of which is a question. [quizPhase] is null for every other phase, which is what makes
 * "update the current question" a no-op on a screen that is not showing one, exactly as the two
 * hand-written `update…` helpers did.
 */
interface QuizSessionState<SELF : QuizSessionState<SELF, P, T>, P : QuizSessionPhase<T, P>, T : QuizDisplayItem> {
    /** The current question, or null when this screen is not asking one. */
    val quizPhase: P?

    /** This state, with [phase] as its question phase. */
    fun withQuizPhase(phase: P): SELF
}
