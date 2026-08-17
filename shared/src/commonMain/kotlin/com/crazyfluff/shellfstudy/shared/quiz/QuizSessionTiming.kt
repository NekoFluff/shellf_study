package com.crazyfluff.shellfstudy.shared.quiz

import com.crazyfluff.shellfstudy.shared.lifecycle.AppForegroundTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Wraps the [ActiveSegmentTracker] plumbing shared by ReviewViewModel/LessonViewModel: the same
 * resume/pause/current-elapsed operations both ViewModels mirror into their uiState, plus the
 * app-foreground-tracker collector that drives resume/pause automatically. [onResume]/[onPause]
 * stay owned by each ViewModel since they differ — Review always re-persists on pause unless the
 * session is complete, Lesson also gates on which phase it's currently in — this only removes the
 * identical tracker plumbing underneath, not that per-VM decision.
 */
class QuizSessionTiming(
    private val onResume: (segmentStartMs: Long) -> Unit,
    private val onPause: (newElapsedMs: Long) -> Unit
) {
    private val tracker = ActiveSegmentTracker()

    var elapsedMs: Long
        get() = tracker.elapsedMs
        set(value) {
            tracker.elapsedMs = value
        }

    val segmentStartMs: Long? get() = tracker.segmentStartMs

    fun currentElapsedMs(nowMs: Long = Clock.System.now().toEpochMilliseconds()): Long =
        tracker.currentElapsedMs(nowMs)

    fun resume() {
        val now = tracker.resume() ?: return
        onResume(now)
    }

    fun pause() {
        val newElapsed = tracker.pause() ?: return
        onPause(newElapsed)
    }

    /** Starts a collector that resumes/pauses the segment on every foreground/background
     *  transition after the first — the initial value is left to the caller's own load/resume
     *  flow, so a ViewModel created while already foregrounded doesn't publish a redundant extra
     *  uiState emission for it. */
    fun wireForegroundTracking(scope: CoroutineScope, appForegroundTracker: AppForegroundTracker) {
        scope.launch {
            appForegroundTracker.isForeground.drop(1).collect { isForeground ->
                if (isForeground) resume() else pause()
            }
        }
    }
}
