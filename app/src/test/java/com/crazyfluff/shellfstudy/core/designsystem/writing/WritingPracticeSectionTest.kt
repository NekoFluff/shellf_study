package com.crazyfluff.shellfstudy.core.designsystem.writing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.data.model.StrokeOrderStroke
import com.crazyfluff.shellfstudy.shared.designsystem.strokeorder.StrokeOrderUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class WritingPracticeSectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val available = StrokeOrderUiState.Available(
        listOf(StrokeOrderStroke(pathData = "M10,10L90,90", labelX = 5f, labelY = 5f))
    )

    private fun drawOneStroke() {
        composeTestRule.onNodeWithTag(WritingPracticeTestTags.CANVAS).performTouchInput {
            down(Offset(10f, 10f))
            moveTo(Offset(60f, 60f))
            up()
        }
    }

    @Test
    fun `renders nothing while loading`() {
        composeTestRule.setContent {
            WritingPracticeSection(strokeOrder = StrokeOrderUiState.Loading, resetKey = 1L)
        }
        composeTestRule.onAllNodesWithTag(WritingPracticeTestTags.SECTION).assertCountEquals(0)
    }

    @Test
    fun `renders nothing when unavailable`() {
        composeTestRule.setContent {
            WritingPracticeSection(strokeOrder = StrokeOrderUiState.Unavailable, resetKey = 1L)
        }
        composeTestRule.onAllNodesWithTag(WritingPracticeTestTags.SECTION).assertCountEquals(0)
    }

    @Test
    fun `available state renders collapsed by default`() {
        composeTestRule.setContent {
            WritingPracticeSection(strokeOrder = available, resetKey = 1L)
        }

        composeTestRule.onNodeWithTag(WritingPracticeTestTags.SECTION).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(WritingPracticeTestTags.CANVAS).assertCountEquals(0)
    }

    @Test
    fun `tapping the expand toggle reveals the canvas and controls`() {
        composeTestRule.setContent {
            WritingPracticeSection(strokeOrder = available, resetKey = 1L)
        }

        composeTestRule.onNodeWithTag(WritingPracticeTestTags.EXPAND_TOGGLE).performClick()

        composeTestRule.onNodeWithTag(WritingPracticeTestTags.CANVAS).assertIsDisplayed()
        composeTestRule.onNodeWithTag(WritingPracticeTestTags.UNDO_BUTTON).assertIsDisplayed()
        composeTestRule.onNodeWithTag(WritingPracticeTestTags.CLEAR_BUTTON).assertIsDisplayed()
        composeTestRule.onNodeWithTag(WritingPracticeTestTags.REFERENCE_TOGGLE).assertIsDisplayed()
    }

    @Test
    fun `undo starts disabled, enables after a stroke, disables again once undone`() {
        composeTestRule.setContent {
            WritingPracticeSection(strokeOrder = available, resetKey = 1L, initiallyExpanded = true)
        }

        composeTestRule.onNodeWithTag(WritingPracticeTestTags.UNDO_BUTTON).assertIsNotEnabled()

        drawOneStroke()
        composeTestRule.onNodeWithTag(WritingPracticeTestTags.UNDO_BUTTON).assertIsEnabled()

        composeTestRule.onNodeWithTag(WritingPracticeTestTags.UNDO_BUTTON).performClick()
        composeTestRule.onNodeWithTag(WritingPracticeTestTags.UNDO_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun `clear returns undo to disabled after multiple strokes`() {
        composeTestRule.setContent {
            WritingPracticeSection(strokeOrder = available, resetKey = 1L, initiallyExpanded = true)
        }

        drawOneStroke()
        drawOneStroke()
        composeTestRule.onNodeWithTag(WritingPracticeTestTags.UNDO_BUTTON).assertIsEnabled()

        composeTestRule.onNodeWithTag(WritingPracticeTestTags.CLEAR_BUTTON).performClick()

        composeTestRule.onNodeWithTag(WritingPracticeTestTags.UNDO_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun `toggling the reference does not crash`() {
        composeTestRule.setContent {
            WritingPracticeSection(strokeOrder = available, resetKey = 1L, initiallyExpanded = true)
        }

        composeTestRule.onNodeWithTag(WritingPracticeTestTags.REFERENCE_TOGGLE).performClick()
        composeTestRule.onNodeWithTag(WritingPracticeTestTags.REFERENCE_TOGGLE).performClick()

        composeTestRule.onNodeWithTag(WritingPracticeTestTags.CANVAS).assertIsDisplayed()
    }
}
