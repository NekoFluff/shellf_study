package com.crazyfluff.shellfstudy.shared.quiz

/** The pause-aware timing fields both LessonUiState.Phase.Quiz and ReviewUiState.Phase.Active
 *  render — mirrors QuizSessionTiming/the per-question timer exactly. Active time accumulated
 *  before the current viewing segment, and (while non-null) when that segment began — see
 *  PausableElapsedTimeText. Segment goes null while the session/question isn't actively being
 *  viewed (app backgrounded, or navigated off-screen), freezing the timer instead of letting it
 *  count straight through that gap. */
data class QuizTimingUiState(
    val sessionActiveElapsedMs: Long = 0L,
    val sessionActiveSegmentStartMs: Long? = null,
    val questionActiveElapsedMs: Long = 0L,
    val questionActiveSegmentStartMs: Long? = null,
    // Non-null once the current question has been answered — freezes the "time on this question"
    // display at this value instead of letting it keep ticking through the feedback screen. Reset
    // to null whenever a fresh, unanswered question is shown.
    val questionElapsedMs: Long? = null
)
