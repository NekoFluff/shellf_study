package com.crazyfluff.shellfstudy.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.crazyfluff.shellfstudy.feature.auth.AuthRoute
import com.crazyfluff.shellfstudy.feature.dashboard.DashboardRoute
import com.crazyfluff.shellfstudy.feature.lesson.LessonRoute
import com.crazyfluff.shellfstudy.feature.review.ReviewRoute
import com.crazyfluff.shellfstudy.feature.settings.SettingsRoute

object ShellfStudyDestinations {
    const val AUTH = "auth"
    const val DASHBOARD = "dashboard"
    const val REVIEW = "review"
    const val LESSON = "lesson"
    const val SETTINGS = "settings"
}

@Composable
fun ShellfStudyNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = ShellfStudyDestinations.AUTH) {
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
