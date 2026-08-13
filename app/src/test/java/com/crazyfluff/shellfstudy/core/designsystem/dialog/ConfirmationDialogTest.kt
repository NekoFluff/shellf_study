package com.crazyfluff.shellfstudy.core.designsystem.dialog

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ConfirmationDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val confirmTag = "confirm_button_tag"

    @Test
    fun `shows title, body, and both button labels`() {
        composeTestRule.setContent {
            ConfirmationDialog(
                title = "Abandon this?",
                text = "This can't be undone.",
                confirmLabel = "Abandon",
                onConfirm = {},
                onDismiss = {},
                confirmButtonTestTag = confirmTag
            )
        }

        composeTestRule.onNodeWithText("Abandon this?").assertExists()
        composeTestRule.onNodeWithText("This can't be undone.").assertExists()
        composeTestRule.onNodeWithText("Abandon").assertExists()
        composeTestRule.onNodeWithText("Cancel").assertExists()
    }

    @Test
    fun `tapping the confirm button invokes onConfirm, not onDismiss`() {
        var confirmed = false
        var dismissed = false
        composeTestRule.setContent {
            ConfirmationDialog(
                title = "Abandon this?",
                text = "This can't be undone.",
                confirmLabel = "Abandon",
                onConfirm = { confirmed = true },
                onDismiss = { dismissed = true },
                confirmButtonTestTag = confirmTag
            )
        }

        composeTestRule.onNodeWithTag(confirmTag).performClick()
        assert(confirmed)
        assert(!dismissed)
    }

    @Test
    fun `tapping cancel invokes onDismiss, not onConfirm`() {
        var confirmed = false
        var dismissed = false
        composeTestRule.setContent {
            ConfirmationDialog(
                title = "Abandon this?",
                text = "This can't be undone.",
                confirmLabel = "Abandon",
                onConfirm = { confirmed = true },
                onDismiss = { dismissed = true },
                confirmButtonTestTag = confirmTag
            )
        }

        composeTestRule.onNodeWithText("Cancel").performClick()
        assert(dismissed)
        assert(!confirmed)
    }

    @Test
    fun `dismissLabel overrides the default Cancel text`() {
        composeTestRule.setContent {
            ConfirmationDialog(
                title = "Abandon this?",
                text = "This can't be undone.",
                confirmLabel = "Abandon",
                onConfirm = {},
                onDismiss = {},
                dismissLabel = "Not now"
            )
        }

        composeTestRule.onNodeWithText("Not now").assertExists()
    }
}
