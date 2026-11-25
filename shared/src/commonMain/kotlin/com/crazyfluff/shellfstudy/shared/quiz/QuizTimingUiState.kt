package com.crazyfluff.shellfstudy.shared.quiz

/** The pause-aware timing both LessonUiState.Phase.Quiz and ReviewUiState.Phase.Active render,
 *  mirroring the session clock: active time accumulated before the current stretch, and (while
 *  non-null) when that stretch began — see PausableElapsedTimeText. The stretch goes null while the
 *  clock is stopped (app backgrounded, screen left), freezing the timer through that gap.
 *
 *  The question has no clock of its own: its time is the session's since [questionStartedAtSessionMs],
 *  so the two always pause together. */
data class QuizTimingUiState(
    val sessionActiveElapsedMs: Long = 0L,
    val sessionActiveSegmentStartMs: Long? = null,
    /** The session's active time when this question was shown (or asked again after an undo). */
    val questionStartedAtSessionMs: Long = 0L,
    // Non-null once the current question has been answered — freezes the "time on this question"
    // display at this value instead of letting it keep ticking through the feedback screen. Reset
    // to null whenever a fresh, unanswered question is shown.
    val questionElapsedMs: Long? = null
)
