package com.crazyfluff.shellfstudy.shared.designsystem.performance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember

/**
 * Reports what the app is doing, so a slow frame can be attributed to something.
 *
 * ## Why this lives in `shared`
 *
 * The recording itself is Android-only — JankStats is an AndroidX library backed by the platform
 * `FrameMetrics` API — but almost everything worth attributing jank to is in shared Compose code: the
 * dashboard's cards, the review screen's question content. Screens therefore report through this
 * seam, and each platform supplies the implementation. On iOS, and anywhere no implementation is
 * provided, [LocalJankStateReporter] defaults to a no-op, so shared code carries no platform
 * dependency and reporting costs nothing when nothing is listening.
 *
 * ## What to report
 *
 * Facts a reader of the log can act on — never measurements. "dashboard, leaderboard=loaded,
 * syncing=true" identifies a composition; "dashboard is slow" does not. Prefer a small set of
 * low-cardinality tags over many, because the point is comparing one log line against another.
 *
 * Reporting is cheap but not free (it allocates a map and crosses into the tracker), so call it from
 * a `remember` keyed on the values that actually change rather than on every recomposition.
 */
val LocalJankStateReporter = compositionLocalOf<(Map<String, String>) -> Unit> { { } }

/** The tag key a screen reports its identity under. Declared here because common code is what sets it. */
const val JANK_STATE_SCREEN = "screen"

/**
 * Reports [tags] as the current state for as long as this composable is in the composition, and
 * retracts nothing on leaving — the next screen's report replaces them, and the tracker attaches
 * state to individual frames rather than holding it.
 *
 * [tags] is spread as alternating key/value pairs to keep call sites readable:
 * `ReportJankState("screen" to "dashboard", "syncing" to "true")`.
 */
/**
 * Builds the stable key for a set of tags, and the map the tracker receives.
 *
 * The key must depend on the tag *values*, not the number of them. Keying on `tags.size` — which this
 * did, and which passed review because it looks like a cheap identity check — meant a screen reporting
 * a fixed number of tags could never update any of them: the computed key never changed, so the
 * remembered map was never rebuilt and the tracker kept the first report forever. Different screens
 * appeared to work only because they happened to report different *counts*.
 *
 * Extracted from the composable so that exact mistake is testable: [ReportJankState] cannot be
 * exercised off-device, but this can. The string build runs per recomposition rather than inside a
 * `remember` — that is the price of correctness here, and it is a few short strings.
 */
internal fun jankStateKey(tags: List<Pair<String, String>>): String =
    tags.joinToString(separator = "\u0000") { "${it.first}=${it.second}" }

@Composable
fun ReportJankState(vararg tags: Pair<String, String>) {
    val reporter = LocalJankStateReporter.current
    val stableTags = tags.toList()
    val key = jankStateKey(stableTags)
    // Remembered on the value key, so an unchanged report does not re-enter the tracker on every
    // recomposition — which would itself be a per-frame cost inside a per-frame harness.
    val stable = remember(key) { stableTags.toMap() }
    DisposableEffect(reporter, stable) {
        reporter(stable)
        onDispose { }
    }
}
