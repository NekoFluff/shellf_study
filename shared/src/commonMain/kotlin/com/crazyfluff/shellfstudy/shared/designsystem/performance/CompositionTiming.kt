package com.crazyfluff.shellfstudy.shared.designsystem.performance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.time.TimeSource

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
 * The monotonic clock [TimedComposition] reads, in nanoseconds.
 *
 * Monotonic rather than wall-clock, because a duration must not move when the system clock does, and
 * a [TimeSource] rather than `System.nanoTime()`, because this file is `commonMain` — `System` does
 * not exist on Kotlin/Native. The JVM tests could not see that; the iOS and metadata compiles could.
 *
 * Overridable so the measurement is testable without a device or a real clock: a test supplies a
 * counter it advances itself and asserts exactly what each composable reported, rather than asserting
 * loose bounds around wall-clock noise.
 */
val LocalCompositionClock = staticCompositionLocalOf<() -> Long> {
    val origin = TimeSource.Monotonic.markNow()
    // Assigned to a val rather than left as the block's trailing expression: a bare lambda there is
    // parsed as a trailing argument to `markNow()`.
    val read: () -> Long = { origin.elapsedNow().inWholeNanoseconds }
    read
}

/**
 * Times how long [content] takes to compose, reporting under [name].
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
 * The clock is read immediately before and immediately after `content()` returns, so the figure is
 * this subtree's own composition. It does **not** include layout or draw: a card can be cheap to
 * compose and expensive to lay out (a measure-heavy FlowRow, say), and that cost lands in the frame
 * timing rather than here. Neither does it include nested subcomposition — `LazyColumn` items that
 * have not composed yet, for instance. Composition is the first thing to rule out, not the last.
 *
 * A composable that is skipped records nothing, which is the useful part: the totals separate
 * "intrinsically expensive" from "recomposing when it should not".
 *
 * ## Why this is a composable and not a `Modifier`
 *
 * This began as `Modifier.timedComposition`, which cannot work. A modifier cannot bracket the
 * composition of the content it is attached to: `Modifier.composed`'s factory block runs *before*
 * that content composes, and the `SideEffect` it registered runs after the *whole* composition
 * commits — so each card's figure was really "time from this card until the end of the pass", every
 * card absorbing everything composed after it. Three cards that each burned 20 ms reported 61 ms,
 * 41 ms and 21 ms, which is also why the first measured ranking came out in tree order and summed to
 * far more than the frame it was meant to explain.
 *
 * The `composed` element was not free either, contrary to what this file used to claim. It has no
 * `equals`, so a modifier chain containing one is never equal to the chain built by the previous
 * composition — which made every card carrying one non-skippable, so it recomposed whenever its
 * parent did. The instrumentation was inflating what it was built to measure. This wrapper emits no
 * layout node, so it is invisible to layout and to skipping; when timing is off it costs one
 * composition-local read.
 */
@Composable
fun TimedComposition(name: String, content: @Composable () -> Unit) {
    if (!LocalCompositionTimingEnabled.current) {
        content()
        return
    }

    val clock = LocalCompositionClock.current
    val recorder = LocalCompositionRecorder.current
    val startedAt = clock()
    content()
    val elapsedNanos = clock() - startedAt
    // Recorded from a SideEffect so nothing happens during composition itself, and so a composition
    // that is discarded before it commits reports nothing rather than a measurement of work undone.
    SideEffect { recorder(name, elapsedNanos) }
}
