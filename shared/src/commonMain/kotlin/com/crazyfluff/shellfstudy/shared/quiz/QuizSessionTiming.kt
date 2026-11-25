package com.crazyfluff.shellfstudy.shared.quiz

import kotlin.time.Clock

/**
 * The session clock: wall-clock time accumulated only while a stretch is running. Started and stopped
 * by [QuizSessionViewModel.driveSessionClock]; a question's time is read off it too (see
 * [QuizTimingUiState.questionStartedAtSessionMs]), so there is only ever this one clock.
 *
 * The owner mirrors [elapsedMs]/[segmentStartMs] into its [QuizTimingUiState] so the timer composable
 * can read them.
 */
class QuizSessionTiming(
    private val onResume: (segmentStartMs: Long) -> Unit,
    private val onPause: (newElapsedMs: Long) -> Unit,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    /** Called once for every stretch that ends — the hook study-time tracking records through. */
    private val onSegmentEnded: (startMs: Long, endMs: Long) -> Unit = { _, _ -> }
) {
    /** Active time accumulated across all *completed* stretches. Settable so a ViewModel resuming a
     *  persisted session can seed it before the clock starts. */
    var elapsedMs: Long = 0L

    /** When the running stretch began, or null while the clock is stopped. */
    var segmentStartMs: Long? = null
        private set

    /** The live total: accumulated time plus whatever the running stretch has added. */
    fun currentElapsedMs(nowMs: Long = clock()): Long =
        elapsedMs + (segmentStartMs?.let { nowMs - it } ?: 0L)

    /** Starts a stretch and notifies [onResume]. No-op while one is already running. */
    fun resume() {
        if (segmentStartMs != null) return
        val now = clock()
        segmentStartMs = now
        onResume(now)
    }

    /** Ends the running stretch: folds it into [elapsedMs], reports it to [onSegmentEnded], then
     *  notifies [onPause]. No-op while stopped. */
    fun pause() {
        val startedAt = segmentStartMs ?: return
        val now = clock()
        elapsedMs += now - startedAt
        segmentStartMs = null
        onSegmentEnded(startedAt, now)
        onPause(elapsedMs)
    }
}
