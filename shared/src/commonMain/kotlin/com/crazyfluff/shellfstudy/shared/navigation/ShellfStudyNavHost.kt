package com.crazyfluff.shellfstudy.shared.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.crazyfluff.shellfstudy.shared.feature.auth.AuthRoute
import com.crazyfluff.shellfstudy.shared.feature.dashboard.DashboardRoute
import com.crazyfluff.shellfstudy.shared.feature.leaderboard.LeaderboardRoute
import com.crazyfluff.shellfstudy.shared.feature.lesson.LessonRoute
import com.crazyfluff.shellfstudy.shared.feature.review.ReviewRoute
import com.crazyfluff.shellfstudy.shared.feature.settings.SettingsRoute
import com.crazyfluff.shellfstudy.shared.feature.splash.SplashRoute
import com.crazyfluff.shellfstudy.shared.notifications.NotificationDeepLink
import kotlinx.serialization.Serializable

sealed interface ShellfStudyDestination {
    @Serializable data object Splash : ShellfStudyDestination
    @Serializable data object Auth : ShellfStudyDestination
    @Serializable data object Dashboard : ShellfStudyDestination
    @Serializable data object Review : ShellfStudyDestination
    @Serializable data object Lesson : ShellfStudyDestination
    @Serializable data object Settings : ShellfStudyDestination
    @Serializable data object Leaderboard : ShellfStudyDestination
}

@Composable
fun ShellfStudyNavHost(
    navController: NavHostController = rememberNavController(),
    pendingDestination: String? = null,
    onPendingDestinationConsumed: () -> Unit = {}
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(pendingDestination, currentBackStackEntry) {
        if (pendingDestination == null) return@LaunchedEffect
        val destination = currentBackStackEntry?.destination ?: return@LaunchedEffect
        if (destination.hasRoute<ShellfStudyDestination.Auth>() || destination.hasRoute<ShellfStudyDestination.Splash>()) {
            return@LaunchedEffect
        }

        val targetDestination = when (pendingDestination) {
            NotificationDeepLink.DESTINATION_REVIEW -> ShellfStudyDestination.Review
            NotificationDeepLink.DESTINATION_LESSON -> ShellfStudyDestination.Lesson
            else -> null
        }
        if (targetDestination != null && !destination.hasRoute(targetDestination::class)) {
            navController.navigateSafely(targetDestination)
        }
        onPendingDestinationConsumed()
    }

    NavHost(navController = navController, startDestination = ShellfStudyDestination.Splash) {
        composable<ShellfStudyDestination.Splash> {
            SplashRoute(
                onNavigateToAuth = {
                    navController.navigateSafely(ShellfStudyDestination.Auth) {
                        popUpTo<ShellfStudyDestination.Splash> { inclusive = true }
                    }
                },
                onNavigateToDashboard = {
                    navController.navigateSafely(ShellfStudyDestination.Dashboard) {
                        popUpTo<ShellfStudyDestination.Splash> { inclusive = true }
                    }
                }
            )
        }
        composable<ShellfStudyDestination.Auth> {
            AuthRoute(
                onAuthenticated = {
                    navController.navigateSafely(ShellfStudyDestination.Dashboard) {
                        popUpTo<ShellfStudyDestination.Auth> { inclusive = true }
                    }
                }
            )
        }
        composable<ShellfStudyDestination.Dashboard> {
            DashboardRoute(
                onStartReview = { navController.navigateSafely(ShellfStudyDestination.Review) },
                onStartLesson = { navController.navigateSafely(ShellfStudyDestination.Lesson) },
                onOpenSettings = { navController.navigateSafely(ShellfStudyDestination.Settings) },
                onOpenLeaderboard = { navController.navigateSafely(ShellfStudyDestination.Leaderboard) },
                onLoggedOut = {
                    navController.navigateSafely(ShellfStudyDestination.Auth) {
                        popUpTo<ShellfStudyDestination.Dashboard> { inclusive = true }
                    }
                },
                pendingDestination = pendingDestination,
                onPendingDestinationConsumed = onPendingDestinationConsumed
            )
        }
        composable<ShellfStudyDestination.Review> {
            ReviewRoute(
                onSessionComplete = { navController.popBackStackSafely() },
                onBack = { navController.popBackStackSafely() }
            )
        }
        composable<ShellfStudyDestination.Lesson> {
            LessonRoute(
                onSessionComplete = { navController.popBackStackSafely() },
                onBack = { navController.popBackStackSafely() }
            )
        }
        composable<ShellfStudyDestination.Settings> {
            SettingsRoute(
                onBack = { navController.popBackStackSafely() },
                onOpenLeaderboard = { navController.navigateSafely(ShellfStudyDestination.Leaderboard) }
            )
        }
        composable<ShellfStudyDestination.Leaderboard> {
            LeaderboardRoute(onBack = { navController.popBackStackSafely() })
        }
    }
}
