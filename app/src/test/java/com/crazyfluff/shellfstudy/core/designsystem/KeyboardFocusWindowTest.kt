package com.crazyfluff.shellfstudy.core.designsystem

import android.view.WindowManager
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.designsystem.components.AppAlertDialog
import com.crazyfluff.shellfstudy.shared.designsystem.components.AppModalBottomSheet
import com.crazyfluff.shellfstudy.shared.designsystem.components.AppTextInputDialog
import com.crazyfluff.shellfstudy.shared.designsystem.dialog.ConfirmationDialog
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog

/**
 * Reads the keyboard-focus flag off the real Android window each wrapper opens.
 *
 * A dialog or sheet without a text field must carry FLAG_ALT_FOCUSABLE_IM, or closing it flashes the
 * keyboard (see KeepWindowOutOfKeyboardFocus). A dialog with a text field must not, or its keyboard
 * never opens. Both failures are invisible in a screen test, which only sees the composition.
 */
@RunWith(AndroidJUnit4::class)
class KeyboardFocusWindowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun latestWindowIsOutOfKeyboardFocus(): Boolean {
        composeTestRule.waitForIdle()
        val flags = ShadowDialog.getLatestDialog().window!!.attributes.flags
        return flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM != 0
    }

    @Test
    fun appAlertDialog_staysOutOfKeyboardFocus() {
        composeTestRule.setContent {
            AppAlertDialog(onDismissRequest = {}, confirmButton = { Text("OK") }, text = { Text("Sure?") })
        }

        assertThat(latestWindowIsOutOfKeyboardFocus()).isTrue()
    }

    @Test
    fun confirmationDialog_staysOutOfKeyboardFocus() {
        composeTestRule.setContent {
            ConfirmationDialog(
                title = "Log out?",
                text = "Sure?",
                confirmLabel = "Log out",
                onConfirm = {},
                onDismiss = {}
            )
        }

        assertThat(latestWindowIsOutOfKeyboardFocus()).isTrue()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun appModalBottomSheet_staysOutOfKeyboardFocus() {
        composeTestRule.setContent {
            AppModalBottomSheet(onDismissRequest = {}) { Text("Details") }
        }

        assertThat(latestWindowIsOutOfKeyboardFocus()).isTrue()
    }

    @Test
    fun appTextInputDialog_remainsAKeyboardTarget() {
        composeTestRule.setContent {
            AppTextInputDialog(onDismissRequest = {}, confirmButton = { Text("Save") }, text = { Text("Nickname") })
        }

        assertThat(latestWindowIsOutOfKeyboardFocus()).isFalse()
    }
}
