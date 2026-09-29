package com.crazyfluff.shellfstudy.shared.designsystem.performance

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * Where composition timings are sent. Defaults to doing nothing, so shared code carries no platform
 * dependency — Android supplies the jank harness's recorder, iOS leaves the default.
 *
 * A composition local rather than a function reference because the recorder is platform-specific and
 * this file is not: the same shape as [LocalJankStateReporter].
 */
val LocalCompositionRecorder = compositionLocalOf<(name: String, nanos: Long) -> Unit> { { _, _ -> } }

/** Whether composition timing is active. False everywhere until a platform turns it on. */
val LocalCompositionTimingEnabled = compositionLocalOf { false }

/**
 * Times how long the composable this is applied to takes to compose, reporting under [name].
 *
 * ## Why this exists
 *
 * Per-frame timing establishes *that* a screen is slow; state tags establish *when*. Neither says
 * which part. On the loaded dashboard the whole tree recomposes together when a fetch resolves, so a
 * stall is the sum of seven cards, two Canvas charts and a paged level grid — and the only way to know
 * where that time goes is to time the parts.
 *
 * ## What it measures, and what it does not
 *
 * `composed {}` runs on each recomposition of the composable it is applied to, and [SideEffect] runs
 * after that composition commits, so together they bracket one component's composition. Because
 * Compose skips a composable whose inputs are unchanged, a component that did not recompose records
 * nothing — which is the useful part: totals distinguish "intrinsically expensive" from "recomposing
 * when it should not".
 *
 * It does **not** measure layout or draw. A card can be cheap to compose and expensive to lay out (a
 * measure-heavy FlowRow, for instance); that cost lands in the frame timing and not here. Composition
 * is the first thing to rule out, not the last.
 *
 * ## Cost
 *
 * When no platform has enabled it this returns [Modifier] unchanged: no `composed`, no clock read, no
 * allocation, nothing for the harness to perturb.
 */
fun Modifier.timedComposition(name: String): Modifier =
    this.composed {
        // Read inside `composed`: a composition local read is a composable call, and `timedComposition`
        // itself is an ordinary function. When timing is off this returns the receiver unchanged, so
        // the only cost in a normal build is the local read.
        if (!LocalCompositionTimingEnabled.current) {
            Modifier
        } else {
            // Read outside `remember` deliberately. A `remember` keyed on the name would capture the
            // first composition and reuse it forever — the exact bug that froze the state tags — and
            // keying on nothing would still skip recompositions, which is what is being measured.
            val recorder = LocalCompositionRecorder.current
            val startedAt = System.nanoTime()
            SideEffect { recorder(name, System.nanoTime() - startedAt) }
            Modifier
        }
    }
