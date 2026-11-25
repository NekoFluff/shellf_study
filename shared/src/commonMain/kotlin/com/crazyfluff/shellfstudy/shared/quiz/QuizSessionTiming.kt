package com.crazyfluff.shellfstudy.shared.quiz

import com.crazyfluff.shellfstudy.shared.lifecycle.AppForegroundTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Tracks the wall-clock time a session (or a single question) is actively being viewed, accumulating
 * time only while a segment is running. The session clock is driven by
 * [QuizSessionViewModel.driveSessionClock]; the per-question clock by [restart]/[freeze] at each
 * question and [wireForegroundTracking] in between.
 *
 * The owner mirrors [elapsedMs]/[segmentStartMs] into its [QuizTimingUiState] so the timer composable
 * can read them.
 */
class QuizSessionTiming(
    private val onResume: (segmentStartMs: Long) -> Unit,
    private val onPause: (newElapsedMs: Long) -> Unit,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    /** Called whenever a running segment ends, by [pause] or [freeze] — the hook study-time
     *  tracking records each stretch through. Not called by [restart], which discards rather than
     *  ends a segment. */
    private val onSegmentEnded: (startMs: Long, endMs: Long) -> Unit = { _, _ -> }
) {
    /** Active time accumulated across all *completed* segments. Settable so a ViewModel resuming a
     *  persisted session can seed it before starting a fresh segment. */
    var elapsedMs: Long = 0L

    /** When the currently-running segment began, or null while nothing is being timed. */
    var segmentStartMs: Long? = null
        private set

    /** The live total at any point: accumulated time plus whatever the running segment has added. */
    fun currentElapsedMs(nowMs: Long = clock()): Long =
        elapsedMs + (segmentStartMs?.let { nowMs - it } ?: 0L)

    /** Starts a new segment and notifies [onResume]. No-ops if one is already running. */
    fun resume() {
        if (segmentStartMs != null) return
        val now = clock()
        segmentStartMs = now
        onResume(now)
    }

    /** Folds the running segment into [elapsedMs] and notifies [onPause]. No-ops if none running. */
    fun pause() {
        val newElapsed = foldRunningSegment() ?: return
        onPause(newElapsed)
    }

    /** Discards any accumulated elapsed time and starts a brand-new segment right now — for
     *  beginning to time a fresh "thing" (e.g. a new quiz question) rather than resuming a paused
     *  one. Unlike [resume], does not invoke [onResume]: a caller starting something fresh already
     *  has its own state update to make (new question, cleared feedback, etc.) and folds the
     *  returned start time into that instead of triggering a second, separate one. */
    fun restart(nowMs: Long = clock()): Long {
        elapsedMs = 0L
        segmentStartMs = nowMs
        return nowMs
    }

    /** Folds the current segment into the accumulated total and returns it, freezing the clock —
     *  for a caller-driven stop (e.g. an answer being graded) rather than a background/foreground
     *  transition. Unlike [pause], does not invoke [onPause]: the caller folds the result into a
     *  state update it's already making. No-ops to the last accumulated total if nothing was
     *  running (the timer wasn't [restart]ed/[resume]d first — shouldn't normally happen). */
    fun freeze(): Long = foldRunningSegment() ?: elapsedMs

    /** Folds the running segment into [elapsedMs] and stops it. Returns the new total, or null if no
     *  segment was running. */
    private fun foldRunningSegment(): Long? {
        val startedAt = segmentStartMs ?: return null
        val now = clock()
        elapsedMs += now - startedAt
        segmentStartMs = null
        onSegmentEnded(startedAt, now)
        return elapsedMs
    }

    /** Pauses on every move to the background and resumes on every return after the first — the
     *  initial value is left to the owner's own [restart], so a ViewModel created in the foreground
     *  doesn't publish a redundant extra uiState emission for it. */
    fun wireForegroundTracking(scope: CoroutineScope, appForegroundTracker: AppForegroundTracker) {
        scope.launch {
            appForegroundTracker.isForeground.drop(1).collect { isForeground ->
                if (isForeground) resume() else pause()
            }
        }
    }
}
