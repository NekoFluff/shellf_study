package com.crazyfluff.shellfstudy.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.navigation.popBackStackSafely
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.Serializable
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Regression coverage for the blank-screen bug: [popBackStackSafely] called with nothing left
 * below the current entry (as happens once the debounce window elapses and a spammed "back"
 * tap gets through) must not pop the last real destination — bare `NavController.popBackStack()`
 * has no such guard and will empty the back stack, leaving [NavHost] with nothing to render.
 *
 * Runs under Robolectric (JVM) against a real [NavHostController] — no device features needed.
 * Pinned to SDK 35: Robolectric 4.15.1 doesn't yet have shadows for this project's targetSdk (37).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PopBackStackSafelyTest {

    @Serializable data object Start
    @Serializable data object Detail

    @get:Rule
    val composeTestRule = createComposeRule()

    @Composable
    private fun TestNavHost(navController: NavHostController) {
        NavHost(navController = navController, startDestination = Start) {
            composable<Start> { Text("start screen") }
            composable<Detail> { Text("detail screen") }
        }
    }

    @Test
    fun poppingBackFromADeeperScreen_returnsToTheDestinationBelowIt() {
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            TestNavHost(navController)
        }

        composeTestRule.runOnIdle { navController.navigate(Detail) }
        composeTestRule.onNodeWithText("detail screen").assertExists()

        composeTestRule.runOnIdle { navController.popBackStackSafely() }

        composeTestRule.onNodeWithText("start screen").assertExists()
    }

    @Test
    fun spammingBack_onceNothingIsLeftBelowTheCurrentEntry_leavesTheCurrentScreenOnScreen() {
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            TestNavHost(navController)
        }

        // Reach Start as the sole real entry, the same shape the production stack ends up in
        // after Auth's inclusive popUpTo — nothing but the graph's own implicit root below it.
        composeTestRule.runOnIdle { navController.navigate(Detail) }
        composeTestRule.runOnIdle { navController.popBackStackSafely() }
        composeTestRule.onNodeWithText("start screen").assertExists()

        // Let the debouncer's 500ms suppression window lapse, then fire another pop attempt —
        // the exact sequence a spammed button tap produces once the window has elapsed.
        Thread.sleep(600)
        composeTestRule.runOnIdle { navController.popBackStackSafely() }

        // Before the fix, NavController.popBackStack() would pop the last real destination and
        // then its own implicit graph root, leaving nothing for NavHost to render.
        composeTestRule.onNodeWithText("start screen").assertExists()
        composeTestRule.runOnIdle {
            assertThat(navController.currentDestination).isNotNull()
            assertThat(navController.currentBackStackEntry).isNotNull()
        }
    }
}
