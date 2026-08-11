package com.crazyfluff.shellfstudy.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.getValue
import com.crazyfluff.shellfstudy.core.notifications.NotificationDeepLink
import com.crazyfluff.shellfstudy.feature.auth.AuthRoute
import com.crazyfluff.shellfstudy.feature.dashboard.DashboardRoute
import com.crazyfluff.shellfstudy.feature.lesson.LessonRoute
import com.crazyfluff.shellfstudy.feature.review.ReviewRoute
import com.crazyfluff.shellfstudy.feature.settings.SettingsRoute
import com.crazyfluff.shellfstudy.feature.splash.SplashRoute

object ShellfStudyDestinations {
    const val SPLASH = "splash"
    const val AUTH = "auth"
    const val DASHBOARD = "dashboard"
    const val REVIEW = "review"
    const val LESSON = "lesson"
    const val SETTINGS = "settings"
}

@Composable
fun ShellfStudyNavHost(
    navController: NavHostController = rememberNavController(),
    pendingDestination: String? = null,
    onPendingDestinationConsumed: () -> Unit = {}
) {
    // Handles both cold start (this recomposes once the post-login popUpTo lands on DASHBOARD)
    // and warm start (the user is already past auth when a notification is tapped) with a single
    // effect — deliberately not also handled inside AUTH's onAuthenticated below, to avoid a race
    // where both paths fire the navigation.
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(pendingDestination, currentBackStackEntry) {
        if (pendingDestination == null) return@LaunchedEffect
        val currentRoute = currentBackStackEntry?.destination?.route ?: return@LaunchedEffect
        if (currentRoute == ShellfStudyDestinations.AUTH || currentRoute == ShellfStudyDestinations.SPLASH) {
            return@LaunchedEffect
        }

        val targetRoute = when (pendingDestination) {
            NotificationDeepLink.DESTINATION_REVIEW -> ShellfStudyDestinations.REVIEW
            NotificationDeepLink.DESTINATION_LESSON -> ShellfStudyDestinations.LESSON
            else -> null
        }
        if (targetRoute != null && targetRoute != currentRoute) {
            navController.navigateSafely(targetRoute)
        }
        onPendingDestinationConsumed()
    }

    NavHost(navController = navController, startDestination = ShellfStudyDestinations.SPLASH) {
        composable(ShellfStudyDestinations.SPLASH) {
            SplashRoute(
                onNavigateToAuth = {
                    navController.navigateSafely(ShellfStudyDestinations.AUTH) {
                        popUpTo(ShellfStudyDestinations.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToDashboard = {
                    navController.navigateSafely(ShellfStudyDestinations.DASHBOARD) {
                        popUpTo(ShellfStudyDestinations.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(ShellfStudyDestinations.AUTH) {
            AuthRoute(
                onAuthenticated = {
                    navController.navigateSafely(ShellfStudyDestinations.DASHBOARD) {
                        popUpTo(ShellfStudyDestinations.AUTH) { inclusive = true }
                    }
                }
            )
        }
        composable(ShellfStudyDestinations.DASHBOARD) {
            DashboardRoute(
                onStartReview = { navController.navigateSafely(ShellfStudyDestinations.REVIEW) },
                onStartLesson = { navController.navigateSafely(ShellfStudyDestinations.LESSON) },
                onOpenSettings = { navController.navigateSafely(ShellfStudyDestinations.SETTINGS) },
                onLoggedOut = {
                    navController.navigateSafely(ShellfStudyDestinations.AUTH) {
                        popUpTo(ShellfStudyDestinations.DASHBOARD) { inclusive = true }
                    }
                }
            )
        }
        composable(ShellfStudyDestinations.REVIEW) {
            ReviewRoute(
                onSessionComplete = { navController.popBackStackSafely() },
                onBack = { navController.popBackStackSafely() }
            )
        }
        composable(ShellfStudyDestinations.LESSON) {
            LessonRoute(
                onSessionComplete = { navController.popBackStackSafely() },
                onBack = { navController.popBackStackSafely() }
            )
        }
        composable(ShellfStudyDestinations.SETTINGS) {
            SettingsRoute(onBack = { navController.popBackStackSafely() })
        }
    }
}
