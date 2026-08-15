package com.crazyfluff.shellfstudy.shared.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

private fun NavController.isCurrentDestinationResumed() =
    currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED

fun <T : Any> NavController.navigateSafely(route: T, builder: NavOptionsBuilder.() -> Unit = {}) {
    if (isCurrentDestinationResumed()) navigate(route, builder)
}

fun NavController.popBackStackSafely() {
    if (isCurrentDestinationResumed()) popBackStack()
}
