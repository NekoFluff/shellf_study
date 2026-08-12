package com.crazyfluff.shellfstudy.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

/**
 * A rapid double-tap on a nav-triggering button (e.g. double-clicking a back arrow) can fire two
 * navigation events before the first one settles. The second call then acts on a back stack
 * that's already mid-transition — popping past the real destination, or navigating from a
 * transitional state — which can leave the NavHost with no valid current entry to render (a
 * blank screen). Only act while the current entry is RESUMED (i.e. the previous navigation has
 * fully settled), so the extra tap is silently dropped instead of corrupting the back stack.
 */
private fun NavController.isCurrentDestinationResumed() =
    currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED

fun <T : Any> NavController.navigateSafely(route: T, builder: NavOptionsBuilder.() -> Unit = {}) {
    if (isCurrentDestinationResumed()) navigate(route, builder)
}

fun NavController.popBackStackSafely() {
    if (isCurrentDestinationResumed()) popBackStack()
}
