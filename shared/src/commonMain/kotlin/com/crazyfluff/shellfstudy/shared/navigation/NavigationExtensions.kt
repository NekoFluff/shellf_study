package com.crazyfluff.shellfstudy.shared.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private fun NavController.isCurrentDestinationResumed() =
    currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED

fun <T : Any> NavController.navigateSafely(route: T, builder: NavOptionsBuilder.() -> Unit = {}) {
    if (isCurrentDestinationResumed()) navigate(route, builder)
}

private val popBackStackDebounceWindow = 500.milliseconds
private var lastPopBackStackRoute: String? = null
private var lastPopBackStackMark: TimeSource.Monotonic.ValueTimeMark? = null

// Not gated on isCurrentDestinationResumed() (unlike navigateSafely): that lifecycle check left
// a window where a single deliberate tap on e.g. "Back to dashboard" got silently dropped with
// no retry. But popBackStack()'s currentDestination updates synchronously, before Compose
// recomposes the screen away — so two rapid taps on the same still-visible button (or nested
// screens like Leaderboard reached via Settings) each fire a real pop, skipping past the
// intended destination. A short time debounce blocks the second of two near-simultaneous taps
// without reintroducing the dropped-single-tap bug.
//
// Keyed on the current destination's route, not just elapsed time: this is a single app-wide
// NavController, so every screen's back button shares this debounce window. Without the route
// key, a legitimate pop on one screen followed within 500ms by an unrelated legitimate pop on a
// different screen (e.g. back out of Settings, then immediately tap "Back to dashboard" on a
// session-complete screen) would silently swallow the second one too.
fun NavController.popBackStackSafely() {
    val currentRoute = currentBackStackEntry?.destination?.route
    val now = TimeSource.Monotonic.markNow()
    val last = lastPopBackStackMark
    if (last != null && lastPopBackStackRoute == currentRoute && now - last < popBackStackDebounceWindow) return
    lastPopBackStackRoute = currentRoute
    lastPopBackStackMark = now
    popBackStack()
}
