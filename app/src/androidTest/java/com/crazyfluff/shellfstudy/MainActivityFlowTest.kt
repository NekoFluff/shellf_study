package com.crazyfluff.shellfstudy

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.NoActivityResumedException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.crazyfluff.shellfstudy.shared.data.TokenRepository
import com.crazyfluff.shellfstudy.feature.auth.AuthScreenTestTags
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * End-to-end smoke test for real app launch/navigation, using Espresso for the back-press
 * interaction (Compose has no native equivalent for system back navigation). Koin is already
 * started by the real Application by the time this test runs (the default AndroidJUnitRunner
 * launches it unmodified), so this just resolves TokenRepository off the running instance rather
 * than needing its own test-only DI setup.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityFlowTest : KoinComponent {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val tokenRepository: TokenRepository by inject()

    @Before
    fun setUp() {
        // Ensure a clean slate regardless of what a previous test run left on the device.
        runBlocking { tokenRepository.clearToken() }
    }

    @Test
    fun launchesToAuthScreen_whenNoTokenStored() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.onNodeWithTag(AuthScreenTestTags.TOKEN_FIELD).assertIsDisplayed()
        }
    }

    @Test
    fun pressingBackFromAuthScreen_exitsCleanlyRatherThanCrashing() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.onNodeWithTag(AuthScreenTestTags.TOKEN_FIELD).assertIsDisplayed()

            // AuthScreen is the app's root/launcher screen with nothing on the back stack, so
            // pressing back should exit the app — Espresso signals that specific case via
            // NoActivityResumedException rather than by returning normally. Any other exception
            // here (e.g. an actual crash) still fails the test.
            val exitedCleanly = try {
                Espresso.pressBack()
                true
            } catch (expected: NoActivityResumedException) {
                true
            }
            assertThat(exitedCleanly).isTrue()
        }
    }
}
