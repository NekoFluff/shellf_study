package com.crazyfluff.shellfstudy.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.crazyfluff.shellfstudy.shared.navigation.ShellfStudyNavHost as SharedShellfStudyNavHost

@Composable
fun ShellfStudyNavHost(
    navController: NavHostController = rememberNavController(),
    pendingDestination: String? = null,
    onPendingDestinationConsumed: () -> Unit = {}
) {
    SharedShellfStudyNavHost(
        navController = navController,
        pendingDestination = pendingDestination,
        onPendingDestinationConsumed = onPendingDestinationConsumed
    )
}
