package com.crazyfluff.shellfstudy.shared.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private fun NavController.isCurrentDestinationResumed() =
    currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED

fun <T : Any> NavController.navigateSafely(route: T, builder: NavOptionsBuilder.() -> Unit = {}) {
    if (isCurrentDestinationResumed()) navigate(route, builder)
}

/**
 * Pure debounce state machine behind [popBackStackSafely] — split out so its (subtle, already
 * regressed once) logic can be unit-tested without a real [NavController].
 *
 * Not gated on [isCurrentDestinationResumed] (unlike [navigateSafely]): that lifecycle check left
 * a window where a single deliberate tap on e.g. "Back to dashboard" got silently dropped with no
 * retry. But `popBackStack()`'s currentDestination updates synchronously, before Compose
 * recomposes the screen away — so two rapid taps on the same still-visible button each fire a real
 * pop, skipping past the intended destination. A short time debounce blocks the second of two
 * near-simultaneous taps without reintroducing the dropped-single-tap bug.
 *
 * Keyed on the destination the *previous* pop landed on, not the route being left — comparing
 * against "where we're leaving from" doesn't work: by the time a rapid second tap on the same
 * still-visible button is processed, the current route has already synchronously advanced to the
 * first tap's destination, so the second tap's "current route" never matches the first tap's
 * "from" route and the debounce silently fails to catch it (this exact bug shipped once already —
 * see git history). Comparing against "where we just landed" correctly catches that trailing tap,
 * because its current route reads as the destination already arrived at. It's still keyed rather
 * than a bare time window — this is a single app-wide NavController, so every screen's back button
 * shares this debounce state, and a genuinely separate pop from a different, currently-active
 * screen is evaluated against *that* screen's own (different) route, so it isn't affected.
 */
internal class PopBackStackDebouncer(
    private val window: Duration = 500.milliseconds,
    private val timeSource: TimeSource = TimeSource.Monotonic
) {
    private var lastDestinationRoute: String? = null
    private var lastMark: TimeMark? = null

    /** Whether a pop attempt landing on [currentRoute] right now should be suppressed. */
    fun shouldSuppress(currentRoute: String?): Boolean {
        val last = lastMark ?: return false
        return lastDestinationRoute == currentRoute && last.elapsedNow() < window
    }

    /** Call once an actual pop has happened, with the destination it landed on. */
    fun recordPop(newDestinationRoute: String?) {
        lastDestinationRoute = newDestinationRoute
        lastMark = timeSource.markNow()
    }
}

private val popBackStackDebouncer = PopBackStackDebouncer()

fun NavController.popBackStackSafely() {
    val currentRoute = currentBackStackEntry?.destination?.route
    if (popBackStackDebouncer.shouldSuppress(currentRoute)) return
    popBackStack()
    popBackStackDebouncer.recordPop(currentBackStackEntry?.destination?.route)
}
