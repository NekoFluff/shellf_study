package com.crazyfluff.shellfstudy.core.designsystem.strokeorder

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.core.data.model.StrokeOrderStroke
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class StrokeOrderDiagramTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val twoStrokes = listOf(
        StrokeOrderStroke(pathData = "M10,10L90,10", labelX = 5f, labelY = 5f),
        StrokeOrderStroke(pathData = "M10,50L90,50", labelX = 5f, labelY = 45f)
    )

    @Test
    fun `renders and plays its draw-in animation without crashing`() {
        composeTestRule.setContent {
            StrokeOrderDiagram(strokes = twoStrokes)
        }

        composeTestRule.onNodeWithTag(StrokeOrderTestTags.DIAGRAM).assertIsDisplayed()
    }

    @Test
    fun `tapping replay restarts the animation without crashing`() {
        composeTestRule.setContent {
            StrokeOrderDiagram(strokes = twoStrokes)
        }

        composeTestRule.onNodeWithTag(StrokeOrderTestTags.REPLAY_BUTTON).performClick()
        composeTestRule.onNodeWithTag(StrokeOrderTestTags.DIAGRAM).assertIsDisplayed()
    }

    @Test
    fun `section shows the diagram and attribution when strokes are available`() {
        composeTestRule.setContent {
            StrokeOrderSection(state = StrokeOrderUiState.Available(twoStrokes))
        }

        composeTestRule.onNodeWithTag(StrokeOrderTestTags.SECTION).assertIsDisplayed()
        composeTestRule.onNodeWithTag(StrokeOrderTestTags.DIAGRAM).assertIsDisplayed()
        composeTestRule.onNodeWithText("Stroke order").assertIsDisplayed()
    }

    @Test
    fun `section renders nothing while loading`() {
        composeTestRule.setContent {
            StrokeOrderSection(state = StrokeOrderUiState.Loading)
        }
        composeTestRule.onAllNodesWithTag(StrokeOrderTestTags.SECTION).assertCountEquals(0)
    }

    @Test
    fun `section renders nothing when unavailable`() {
        composeTestRule.setContent {
            StrokeOrderSection(state = StrokeOrderUiState.Unavailable)
        }
        composeTestRule.onAllNodesWithTag(StrokeOrderTestTags.SECTION).assertCountEquals(0)
    }
}
