package com.crazyfluff.shellfstudy.shared.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Whether a sync pass is running right now, for the jank harness to tag frames with.
 *
 * ## Why this is a separate object rather than a property of [SyncOrchestrator]
 *
 * The tracker that reads it lives in `shared/androidMain` and `SyncOrchestrator` is `commonMain`, so
 * the dependency can only run one way — androidMain sees commonMain, never the reverse. Publishing
 * here lets the orchestrator write and the tracker read without either knowing about the other, and
 * without the Android-only harness becoming a constructor argument of a class iOS also builds.
 *
 * ## Why a count and not a boolean
 *
 * Overlapping passes are legitimate: the periodic worker, a dashboard resume and a pull-to-refresh can
 * all be in flight. [SyncOrchestrator]'s mutex serializes them within a process, but a boolean toggled
 * independently by each would still let the first pass to finish report "not syncing" while another is
 * mid-flight. A count cannot be cleared early by one participant.
 *
 * This is telemetry, so it is a plain global rather than something threaded through DI: nothing reads
 * it but the debug-only harness, and it must not become a reason for production code to take on a
 * dependency.
 */
object SyncActivity {

    private val inFlightPasses = MutableStateFlow(0)
    private val _isSyncing = MutableStateFlow(false)

    /** True while at least one sync pass is running. Collected by the harness. */
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /** Marks a pass as started. Every call must be paired with [passFinished], including on failure. */
    fun passStarted() {
        inFlightPasses.update { it + 1 }
        publish()
    }

    /** Marks a pass as finished. */
    fun passFinished() {
        // Coerced at zero so a mismatched pair (a double-finish from a bug elsewhere) cannot drive the
        // count negative, which would keep `isSyncing` stuck true forever.
        inFlightPasses.update { (it - 1).coerceAtLeast(0) }
        publish()
    }

    private fun publish() {
        val syncing = inFlightPasses.value > 0
        if (_isSyncing.value != syncing) _isSyncing.value = syncing
    }
}
