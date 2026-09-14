package com.crazyfluff.shellfstudy.shared.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.crazyfluff.shellfstudy.shared.feature.auth.AuthRoute
import com.crazyfluff.shellfstudy.shared.feature.dashboard.DashboardRoute
import com.crazyfluff.shellfstudy.shared.feature.lastsession.LastSessionSummaryRoute
import com.crazyfluff.shellfstudy.shared.feature.leaderboard.LeaderboardRoute
import com.crazyfluff.shellfstudy.shared.feature.lesson.LessonRoute
import com.crazyfluff.shellfstudy.shared.feature.review.ReviewRoute
import com.crazyfluff.shellfstudy.shared.feature.settings.SettingsRoute
import com.crazyfluff.shellfstudy.shared.feature.splash.SplashRoute
import kotlinx.serialization.Serializable

sealed interface ShellfStudyDestination {
    @Serializable data object Splash : ShellfStudyDestination
    @Serializable data object Auth : ShellfStudyDestination
    @Serializable data object Dashboard : ShellfStudyDestination
    @Serializable data object Review : ShellfStudyDestination
    @Serializable data object Lesson : ShellfStudyDestination
    @Serializable data object Settings : ShellfStudyDestination
    @Serializable data object Leaderboard : ShellfStudyDestination
    @Serializable data object LastSessionSummary : ShellfStudyDestination
}

@Composable
fun ShellfStudyNavHost(
    navController: NavHostController = rememberNavController(),
    pendingDestination: String? = null,
    onPendingDestinationConsumed: () -> Unit = {}
) {
    // pendingDestination only ever carries DESTINATION_DASHBOARD today (see NotificationDeepLink) —
    // DashboardRoute below is handed it directly and consumes it itself once composed.
    NavHost(navController = navController, startDestination = ShellfStudyDestination.Splash) {
        composable<ShellfStudyDestination.Splash> {
            SplashRoute(
                onNavigateToAuth = {
                    navController.navigate(ShellfStudyDestination.Auth) {
                        popUpTo<ShellfStudyDestination.Splash> { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToDashboard = {
                    navController.navigate(ShellfStudyDestination.Dashboard) {
                        popUpTo<ShellfStudyDestination.Splash> { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable<ShellfStudyDestination.Auth> {
            AuthRoute(
                onAuthenticated = {
                    navController.navigate(ShellfStudyDestination.Dashboard) {
                        popUpTo<ShellfStudyDestination.Auth> { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable<ShellfStudyDestination.Dashboard> {
            DashboardRoute(
                onStartReview = { navController.navigate(ShellfStudyDestination.Review) { launchSingleTop = true } },
                onStartLesson = { navController.navigate(ShellfStudyDestination.Lesson) { launchSingleTop = true } },
                onOpenSettings = { navController.navigate(ShellfStudyDestination.Settings) { launchSingleTop = true } },
                onOpenLeaderboard = { navController.navigate(ShellfStudyDestination.Leaderboard) { launchSingleTop = true } },
                onOpenLastSessionSummary = { navController.navigate(ShellfStudyDestination.LastSessionSummary) { launchSingleTop = true } },
                onLoggedOut = {
                    navController.navigate(ShellfStudyDestination.Auth) {
                        popUpTo<ShellfStudyDestination.Dashboard> { inclusive = true }
                        launchSingleTop = true
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
                onOpenLeaderboard = { navController.navigate(ShellfStudyDestination.Leaderboard) { launchSingleTop = true } }
            )
        }
        composable<ShellfStudyDestination.Leaderboard> {
            LeaderboardRoute(onBack = { navController.popBackStackSafely() })
        }
        composable<ShellfStudyDestination.LastSessionSummary> {
            LastSessionSummaryRoute(onBack = { navController.popBackStackSafely() })
        }
    }
}
