package com.crazyfluff.shellfstudy.shared.designsystem.performance

import androidx.compose.runtime.Composable
import com.crazyfluff.shellfstudy.shared.quiz.QuizQuestionState

/**
 * What a quiz screen is doing with the current question, as far as the jank harness is concerned.
 *
 * Features differ in whether a question is on screen at all — a lesson is in Study before it quizzes,
 * a review is Active throughout — so each maps its own phase to one of these, and everything below
 * the mapping is shared.
 */
enum class QuizAnswerJankState(val tag: String) {
    /** No question on screen, so the answer tags would describe nothing. */
    NotAnswering("n/a"),

    /** A question is showing and has not been graded: the frame belongs to typing or revealing. */
    Answering("answering"),

    /**
     * Graded, with feedback on screen but the next question not started. This is the window the
     * per-answer work runs through — the optimistic SRS write, the outbox enqueue, the session
     * persist — which is why it is worth distinguishing from the reveal below.
     */
    FeedbackHidden("feedback_hidden"),

    /** Graded, with the reading revealed — the reading-reveal effects run in this window. */
    FeedbackRevealed("feedback_revealed"),
}

/**
 * The tag set a quiz screen reports, as a plain list so it can be asserted off-device — the same
 * reason [jankStateKey] is extracted from [ReportJankState]. The lesson and review screens reported
 * this identically from two hand-written copies, which is the shape of bug that let the state tags
 * freeze at their first value: two copies agree until one is edited.
 */
internal fun quizSessionJankTags(
    screen: String,
    phaseName: String,
    answerState: QuizAnswerJankState,
    hasRankChange: Boolean
): List<Pair<String, String>> = listOf(
    JANK_STATE_SCREEN to screen,
    "phase" to phaseName,
    "answer" to answerState.tag,
    "rankChange" to if (hasRankChange) "shown" else "none"
)

/** Reports a quiz session's state — see [quizSessionJankTags] for what the tags mean. */
@Composable
fun ReportQuizSessionJankState(
    screen: String,
    phaseName: String,
    answerState: QuizAnswerJankState,
    hasRankChange: Boolean
) {
    ReportJankState(*quizSessionJankTags(screen, phaseName, answerState, hasRankChange).toTypedArray())
}

/** Which answering state a question is in, for the jank harness — [QuizAnswerJankState.NotAnswering]
 *  when no question is on screen. */
fun QuizQuestionState<*>?.answerJankState(): QuizAnswerJankState {
    val grade = this?.grade
    return when {
        this == null -> QuizAnswerJankState.NotAnswering
        grade == null -> QuizAnswerJankState.Answering
        grade.answerRevealed -> QuizAnswerJankState.FeedbackRevealed
        else -> QuizAnswerJankState.FeedbackHidden
    }
}
