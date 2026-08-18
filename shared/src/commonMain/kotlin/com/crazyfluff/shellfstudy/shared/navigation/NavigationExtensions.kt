package com.crazyfluff.shellfstudy.shared.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

private fun NavController.isCurrentDestinationResumed() =
    currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED

fun <T : Any> NavController.navigateSafely(route: T, builder: NavOptionsBuilder.() -> Unit = {}) {
    if (isCurrentDestinationResumed()) navigate(route, builder)
}

// Unlike navigateSafely, this isn't gated on isCurrentDestinationResumed(): popBackStack()
// already no-ops safely when there's nothing valid to pop, and every screen this is called
// from sits directly on top of Dashboard, so a pop always lands correctly. Gating on the
// current entry's transient lifecycle state instead had a window where a single deliberate
// tap (e.g. "Back to dashboard" on the session-complete screen) got silently dropped with no
// retry, forcing the user to tap repeatedly.
fun NavController.popBackStackSafely() {
    popBackStack()
}
