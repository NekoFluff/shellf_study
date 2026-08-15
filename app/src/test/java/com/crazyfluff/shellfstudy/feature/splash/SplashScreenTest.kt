package com.crazyfluff.shellfstudy.feature.splash

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.feature.splash.SplashScreen
import com.crazyfluff.shellfstudy.shared.feature.splash.SplashScreenTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Runs under Robolectric (JVM) — SplashScreen is purely static, no ViewModel or device features.
 * Pinned to SDK 35: Robolectric 4.15.1 doesn't yet have shadows for this project's targetSdk (37).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SplashScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `splash screen shows a loading indicator`() {
        composeTestRule.setContent { SplashScreen() }

        composeTestRule
            .onNodeWithTag(SplashScreenTestTags.LOADING_INDICATOR)
            .assertIsDisplayed()
    }
}
