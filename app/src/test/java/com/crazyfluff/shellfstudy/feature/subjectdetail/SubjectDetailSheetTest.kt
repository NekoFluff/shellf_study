package com.crazyfluff.shellfstudy.feature.subjectdetail

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.DetailRevealMode
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.SubjectDetailTestTags
import com.crazyfluff.shellfstudy.shared.feature.subjectdetail.SubjectDetailSheet
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The collapsed peek strip is the only part of [SubjectDetailSheet] reachable without an open
 * body (the body needs the Koin-provided view model), so that is what these cover: the strip's
 * copy and its tap-to-open behavior.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SubjectDetailSheetTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun collapsedSheet_invitesATap() {
        setSheet()

        composeTestRule.onNodeWithText("Tap to view details").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Swipe up for details").assertCountEquals(0)
    }

    @Test
    fun collapsedSheet_peekStripInvokesToggle() {
        var toggled = false
        setSheet(onToggle = { toggled = true })

        composeTestRule.onNodeWithTag(SubjectDetailTestTags.PEEK_HANDLE).performClick()

        assertThat(toggled).isTrue()
    }

    private fun setSheet(onToggle: () -> Unit = {}) {
        composeTestRule.setContent {
            SubjectDetailSheet(
                subjectId = 440,
                expanded = false,
                onToggle = onToggle,
                onDismiss = {},
                revealMode = DetailRevealMode.FULL,
                isAnswered = true,
                questionType = null
            )
        }
    }
}
