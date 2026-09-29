package com.crazyfluff.shellfstudy.shared.designsystem.performance

import android.util.Log
import android.view.View
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import java.util.WeakHashMap

/**
 * Records per-frame UI-thread timing with JankStats, tagged with what the app was doing at the time.
 *
 * ## Why this exists
 *
 * Everything before this was measured at the wrong layer. Database work is measurable through query
 * plans and write counts, and that is all now fixed and verified. But "the dashboard feels slow" is a
 * claim about frames, and the only frame evidence available was `dumpsys gfxinfo` — which reports a
 * *distribution* with no idea what the app was doing when a slow frame happened. That is enough to
 * know jank exists, and useless for knowing which part of the screen causes it.
 *
 * JankStats fills that gap. [FrameData] carries the UI-thread duration of each frame plus any state
 * tags attached while that frame was produced, so a slow frame arrives annotated with "the dashboard
 * was on screen, the leaderboard had loaded, no sync was running". That turns a distribution into a
 * pointer at a specific composable.
 *
 * ## Reading the output
 *
 * ```
 * adb logcat -s ShellfStudyJank
 * ```
 *
 * `SUMMARY` is emitted every [SUMMARY_INTERVAL_FRAMES] frames with the duration distribution and the
 * share of frames over each budget — the number to compare between builds. `SLOW` lines name
 * individual stalled frames with their tags, which is what identifies *where* a stall happens and is
 * the part `dumpsys gfxinfo` could never give.
 *
 * ## Cost, and why it is gated
 *
 * JankStats calls a listener on every frame, so this must not ship. [isEnabled] is false unless
 * [enable] is called, which `MainActivity` does for debug builds only, plus a system-property override
 * so a release build can be profiled without a code change:
 *
 * ```
 * adb shell setprop debug.shellfstudy.jankstats 1
 * ```
 */
object JankStatsTracker {

    const val TAG = "ShellfStudyJank"

    /** Frames between summary lines — roughly five seconds of interaction at 120 Hz. */
    const val SUMMARY_INTERVAL_FRAMES = 600

    /**
     * A frame slower than this gets its own log line. Above both the 120 Hz budget (8.3 ms) and the
     * 60 Hz one (16.7 ms), so the log shows genuinely dropped frames rather than every frame that
     * merely missed the fastest refresh rate.
     */
    const val STALLED_FRAME_NANOS = 33_000_000L

    private const val BUDGET_120HZ_NANOS = 8_333_333L
    private const val BUDGET_60HZ_NANOS = 16_666_666L

    /** The state key screens report their identity under. */
    const val STATE_SCREEN = "screen"

    @Volatile
    var isEnabled: Boolean = false
        private set

    /**
     * The decor view JankStats is tracking. Held because [PerformanceMetricsState] state has to be
     * attached through the same hierarchy JankStats used — and `JankStats` exposes no accessor for the
     * view it was created with, so it is remembered here at registration.
     */
    private var trackedView: View? = null
    private var jankStats: JankStats? = null

    private val counters = FrameCounters()

    /** Turns recording on. Idempotent. */
    fun enable() {
        isEnabled = true
    }

    /**
     * Starts tracking [activity]'s window. Must be called once the decor view is attached —
     * `onResume` is the first point where that is reliably true, and JankStats reads
     * `window.peekDecorView()` at construction.
     *
     * Idempotent: a second call for an already-tracked window is ignored, because registering twice
     * would deliver every frame twice and double-count everything.
     */
    fun start(activity: android.app.Activity) {
        if (!isEnabled || jankStats != null) return
        runCatching {
            val decorView = activity.window.decorView
            trackedView = decorView
            jankStats = JankStats.createAndTrack(activity.window) { frame -> onFrame(frame) }
            // Resolved *after* createAndTrack, never before. `getHolderForHierarchy` returns a Holder
            // whose `state` is populated by JankStats' own constructor — asking for it first yields a
            // non-null Holder with a null `state`, which silently disabled every state tag (the frames
            // logged fine, they just all said `no-state`).
            stateHolder = PerformanceMetricsState.getHolderForHierarchy(decorView)?.state
            Log.i(TAG, "tracking started (stateHolder=${stateHolder != null})")
        }.onFailure { Log.w(TAG, "could not start JankStats: ${it.message}") }
    }

    /** Stops tracking. Called from `onDestroy` so a recreated Activity can register again. */
    fun stop() {
        jankStats?.let { runCatching { it.isTrackingEnabled = false } }
        jankStats = null
        trackedView = null
        stateHolder = null
        reportedState.clear()
    }

    /** Cached state holder for [trackedView], resolved once rather than per tag update. */
    private var stateHolder: PerformanceMetricsState? = null

    /** What is currently attached to [stateHolder], so [setState] can diff rather than re-put. */
    private val reportedState = mutableMapOf<String, String>()

    /**
     * Replaces the tags attached to subsequent frames.
     *
     * Callers report *facts about what is on screen* — `screen=dashboard`, `leaderboard=loaded` — never
     * measurements. A frame that stalls is then logged with whichever of these were true while it was
     * produced, which is the whole point of the harness.
     *
     * Replaced, not merged: the tags are a complete description of the current state, so anything the
     * new report omits is removed. That matters because a tag left attached after its screen is gone
     * would mislabel every later frame.
     */
    fun setState(tags: Map<String, String>) {
        if (!isEnabled) return
        // Null before start() has run, which is expected: screens compose before the window is
        // resumable. Reports during that window are simply dropped.
        val state = stateHolder ?: return
        runCatching {
            // Diffed against what is currently attached: keys the new report drops are removed, and
            // keys whose value changed are replaced. `putState` rather than `putSingleFrameState`
            // deliberately — a single-frame tag is only attached to the frame it is published on, and
            // a stall almost never happens on that exact frame, so the tags would be missing from
            // precisely the log lines that need them. These tags describe what is on screen now, which
            // is persistent by nature.
            (reportedState.keys - tags.keys).forEach { stale -> state.removeState(stale) }
            tags.forEach { (key, value) -> if (reportedState[key] != value) state.putState(key, value) }
            reportedState.clear()
            reportedState.putAll(tags)
        }
    }

    private fun onFrame(frame: FrameData) {
        val uiNanos = frame.frameDurationUiNanos
        val screen = frame.states.firstOrNull { it.key == STATE_SCREEN }?.value ?: "unknown"

        if (uiNanos >= STALLED_FRAME_NANOS) {
            Log.i(TAG, "SLOW ${ms(uiNanos)} | isJank=${frame.isJank} | " + describeStates(frame))
        }

        if (counters.record(uiNanos, screen)) logSummary()
    }

    private fun logSummary() {
        Log.i(TAG, counters.summaryAndReset())
    }

    private fun describeStates(frame: FrameData): String =
        if (frame.states.isEmpty()) {
            "no-state"
        } else {
            frame.states.joinToString(",") { "${it.key}=${it.value}" }
        }

    private fun ms(nanos: Long): String = "%.1fms".format(nanos / 1_000_000.0)

    /**
     * Frame accounting for one summary window.
     *
     * Separated from the tracker so the arithmetic — which is the part that can be silently wrong, and
     * the part a reader has to trust — is testable on the JVM without a device or a live window.
     */
    @androidx.annotation.VisibleForTesting
    class FrameCounters(private val window: Int = SUMMARY_INTERVAL_FRAMES) {
        private var durations = LongArray(window)
        private var count = 0
        private var frames = 0
        private var over120 = 0
        private var over60 = 0
        private val byScreen = mutableMapOf<String, Int>()
        private val stalledByScreen = mutableMapOf<String, Int>()

        /** Returns true when the window is full and a summary is due. */
        fun record(uiNanos: Long, screen: String): Boolean {
            frames++
            if (uiNanos > BUDGET_120HZ_NANOS) over120++
            if (uiNanos > BUDGET_60HZ_NANOS) over60++
            if (count < durations.size) durations[count++] = uiNanos
            byScreen[screen] = (byScreen[screen] ?: 0) + 1
            if (uiNanos >= STALLED_FRAME_NANOS) {
                stalledByScreen[screen] = (stalledByScreen[screen] ?: 0) + 1
            }
            return frames >= window
        }

        fun summaryAndReset(): String {
            val sorted = durations.copyOf(count).also { it.sort() }
            if (sorted.isEmpty()) return "SUMMARY frames=0"

            fun pct(p: Double) = ms(sorted[((sorted.size - 1) * p).toInt()])
            val line = "SUMMARY frames=$frames p50=${pct(0.5)} p90=${pct(0.9)} " +
                "p99=${pct(0.99)} max=${ms(sorted.last())} " +
                "over8ms=${percent(over120, frames)} over17ms=${percent(over60, frames)} " +
                "| stalled=$stalledByScreen | byScreen=$byScreen"

            durations = LongArray(window)
            count = 0
            frames = 0
            over120 = 0
            over60 = 0
            byScreen.clear()
            stalledByScreen.clear()
            return line
        }

        private fun percent(part: Int, total: Int) = if (total == 0) "0%" else "${part * 100 / total}%"

        private fun ms(nanos: Long) = "%.1fms".format(nanos / 1_000_000.0)
    }
}
