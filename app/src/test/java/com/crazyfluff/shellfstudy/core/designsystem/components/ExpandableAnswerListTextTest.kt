package com.crazyfluff.shellfstudy.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.designsystem.components.ExpandableAnswerListText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val TEST_TAG = "expandable_answer_list_text"

/** Isolated tests for [ExpandableAnswerListText] against a fixed-width container — the shared
 *  truncate/expand widget behind both the review/lesson feedback text and auxiliary meanings,
 *  tested directly rather than through a full screen so the available width is deterministic.
 *
 *  The visual-overflow latch this widget adds (see its kdoc) isn't covered here: Robolectric's
 *  Compose text layout doesn't compute [androidx.compose.ui.text.TextLayoutResult.hasVisualOverflow]
 *  accurately (confirmed against a bare `Text` — it reports `false` even for a string that
 *  clearly can't fit a 20dp-wide box), so a JVM test can't observe that path. Verify it on a
 *  device/emulator instead. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ExpandableAnswerListTextTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun underCountCap_andFitsAvailableWidth_isNotExpandable() {
        composeTestRule.setContent {
            Box(Modifier.width(300.dp)) {
                ExpandableAnswerListText(
                    joined = "Aqua, H2O",
                    resetKey = "Aqua, H2O",
                    modifier = Modifier.testTag(TEST_TAG)
                )
            }
        }

        composeTestRule.onNodeWithText("Aqua, H2O").assertIsDisplayed()
        composeTestRule.onNodeWithTag(TEST_TAG).assertHasNoClickAction()
    }

    @Test
    fun overCountCap_tapExpandsThenCollapses() {
        composeTestRule.setContent {
            Box(Modifier.width(300.dp)) {
                ExpandableAnswerListText(
                    joined = "A, B, C, D, E",
                    resetKey = "A, B, C, D, E",
                    modifier = Modifier.testTag(TEST_TAG)
                )
            }
        }

        composeTestRule.onNodeWithText("A, B, C +2 more").assertIsDisplayed()

        composeTestRule.onNodeWithTag(TEST_TAG).performClick()
        composeTestRule.onNodeWithText("A, B, C, D, E").assertIsDisplayed()

        composeTestRule.onNodeWithTag(TEST_TAG).performClick()
        composeTestRule.onNodeWithText("A, B, C +2 more").assertIsDisplayed()
    }

    @Test
    fun prefix_isPrependedToDisplayText() {
        composeTestRule.setContent {
            Box(Modifier.width(300.dp)) {
                ExpandableAnswerListText(
                    joined = "Water",
                    resetKey = "Water",
                    prefix = "Also accepted:",
                    modifier = Modifier.testTag(TEST_TAG)
                )
            }
        }

        composeTestRule.onNodeWithText("Also accepted: Water").assertIsDisplayed()
    }
}
