package com.crazyfluff.shellfstudy.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavDestination.Companion.hasRoute
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
import kotlinx.serialization.Serializable

/** Type-safe Navigation-Compose routes. Named `ShellfStudyDestination`, not `XRoute`, to avoid
 *  colliding with the `@Composable fun XRoute(...)` entry points this same file calls by simple
 *  name (`AuthRoute`, `DashboardRoute`, etc.) — those are unrelated, feature-owned composables. */
sealed interface ShellfStudyDestination {
    @Serializable
    data object Splash : ShellfStudyDestination

    @Serializable
    data object Auth : ShellfStudyDestination

    @Serializable
    data object Dashboard : ShellfStudyDestination

    @Serializable
    data object Review : ShellfStudyDestination

    @Serializable
    data object Lesson : ShellfStudyDestination

    @Serializable
    data object Settings : ShellfStudyDestination
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
            SettingsRoute(onBack = { navController.popBackStackSafely() })
        }
    }
}
