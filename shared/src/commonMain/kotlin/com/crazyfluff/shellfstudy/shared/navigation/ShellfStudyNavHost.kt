package com.crazyfluff.shellfstudy.shared.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.crazyfluff.shellfstudy.shared.designsystem.performance.JANK_STATE_SCREEN
import com.crazyfluff.shellfstudy.shared.designsystem.performance.ReportJankState
import com.crazyfluff.shellfstudy.shared.feature.auth.AuthRoute
import com.crazyfluff.shellfstudy.shared.feature.dashboard.DashboardRoute
import com.crazyfluff.shellfstudy.shared.feature.lastsession.LastSessionSummaryRoute
import com.crazyfluff.shellfstudy.shared.feature.studytime.StudyTimeRoute
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
    @Serializable data object StudyTime : ShellfStudyDestination
}

/**
 * Tags the frames a destination produces with its name, so a stalled frame in the jank log says which
 * screen it came from — the one thing `dumpsys gfxinfo` could never tell us.
 *
 * Applied uniformly to every destination rather than left to each screen: a screen that forgot to
 * report would appear in the log as `screen=unknown` and its jank would be unattributable, which is
 * exactly the situation this harness exists to end.
 */
@Composable
private fun ReportedDestination(name: String, content: @Composable () -> Unit) {
    ReportJankState(JANK_STATE_SCREEN to name)
    content()
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
            ReportedDestination("splash") {
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
        }
        composable<ShellfStudyDestination.Auth> {
            ReportedDestination("auth") {
                AuthRoute(
                    onAuthenticated = {
                        navController.navigate(ShellfStudyDestination.Dashboard) {
                            popUpTo<ShellfStudyDestination.Auth> { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
        composable<ShellfStudyDestination.Dashboard> {
            ReportedDestination("dashboard") {
                DashboardRoute(
                    onStartReview = { navController.navigate(ShellfStudyDestination.Review) { launchSingleTop = true } },
                    onStartLesson = { navController.navigate(ShellfStudyDestination.Lesson) { launchSingleTop = true } },
                    onOpenSettings = { navController.navigate(ShellfStudyDestination.Settings) { launchSingleTop = true } },
                    onOpenLeaderboard = { navController.navigate(ShellfStudyDestination.Leaderboard) { launchSingleTop = true } },
                    onOpenLastSessionSummary = { navController.navigate(ShellfStudyDestination.LastSessionSummary) { launchSingleTop = true } },
                    onOpenStudyTime = {
                        navController.navigate(ShellfStudyDestination.StudyTime) { launchSingleTop = true }
                    },
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
        }
        composable<ShellfStudyDestination.Review> {
            ReportedDestination("review") {
                ReviewRoute(
                    onSessionComplete = { navController.popBackStackSafely() },
                    onBack = { navController.popBackStackSafely() }
                )
            }
        }
        composable<ShellfStudyDestination.Lesson> {
            ReportedDestination("lesson") {
                LessonRoute(
                    onSessionComplete = { navController.popBackStackSafely() },
                    onBack = { navController.popBackStackSafely() }
                )
            }
        }
        composable<ShellfStudyDestination.Settings> {
            ReportedDestination("settings") {
                SettingsRoute(
                    onBack = { navController.popBackStackSafely() },
                    onOpenLeaderboard = { navController.navigate(ShellfStudyDestination.Leaderboard) { launchSingleTop = true } }
                )
            }
        }
        composable<ShellfStudyDestination.Leaderboard> {
            ReportedDestination("leaderboard") {
                LeaderboardRoute(onBack = { navController.popBackStackSafely() })
            }
        }
        composable<ShellfStudyDestination.LastSessionSummary> {
            ReportedDestination("last_session_summary") {
                LastSessionSummaryRoute(onBack = { navController.popBackStackSafely() })
            }
        }
        composable<ShellfStudyDestination.StudyTime> {
            ReportedDestination("study_time") {
                StudyTimeRoute(onBack = { navController.popBackStackSafely() })
            }
        }
    }
}
