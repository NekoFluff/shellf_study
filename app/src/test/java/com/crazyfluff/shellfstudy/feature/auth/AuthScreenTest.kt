package com.crazyfluff.shellfstudy.feature.auth

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Runs under Robolectric (JVM) — this screen is driven purely by state, no device features needed.
 * Pinned to SDK 35: Robolectric 4.15.1 doesn't yet have shadows for this project's targetSdk (37).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AuthScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsTokenFieldAndSubmitButton_whenNotLoading() {
        composeTestRule.setContent {
            AuthScreen(
                uiState = AuthUiState(),
                onTokenInputChange = {},
                onSubmit = {}
            )
        }

        composeTestRule.onNodeWithTag(AuthScreenTestTags.TOKEN_FIELD).assertIsDisplayed()
        composeTestRule.onNodeWithTag(AuthScreenTestTags.SUBMIT_BUTTON).assertIsDisplayed()
    }

    @Test
    fun typingIntoTokenField_invokesCallbackWithTypedText() {
        var lastValue = ""
        composeTestRule.setContent {
            AuthScreen(
                uiState = AuthUiState(),
                onTokenInputChange = { lastValue = it },
                onSubmit = {}
            )
        }

        composeTestRule.onNodeWithTag(AuthScreenTestTags.TOKEN_FIELD).performTextInput("abc123")

        assert(lastValue == "abc123") { "Expected 'abc123' but got '$lastValue'" }
    }

    @Test
    fun clickingSubmit_invokesOnSubmit() {
        var submitted = false
        composeTestRule.setContent {
            AuthScreen(
                uiState = AuthUiState(tokenInput = "abc123"),
                onTokenInputChange = {},
                onSubmit = { submitted = true }
            )
        }

        composeTestRule.onNodeWithTag(AuthScreenTestTags.SUBMIT_BUTTON).performClick()

        assert(submitted)
    }

    @Test
    fun tokenLink_opensPersonalAccessTokensUrl() {
        var openedUrl: String? = null
        val fakeUriHandler = object : UriHandler {
            override fun openUri(uri: String) {
                openedUrl = uri
            }
        }
        composeTestRule.setContent {
            CompositionLocalProvider(LocalUriHandler provides fakeUriHandler) {
                AuthScreen(
                    uiState = AuthUiState(),
                    onTokenInputChange = {},
                    onSubmit = {}
                )
            }
        }

        composeTestRule.onNodeWithTag(AuthScreenTestTags.TOKEN_LINK).performClick()

        assert(openedUrl == "https://www.wanikani.com/settings/personal_access_tokens") {
            "Expected the personal access tokens URL but got '$openedUrl'"
        }
    }

    @Test
    fun showsErrorMessage_whenPresent() {
        composeTestRule.setContent {
            AuthScreen(
                uiState = AuthUiState(errorMessage = "Invalid API token."),
                onTokenInputChange = {},
                onSubmit = {}
            )
        }

        composeTestRule.onNodeWithText("Invalid API token.").assertIsDisplayed()
    }
}
