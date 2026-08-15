package com.crazyfluff.shellfstudy.core.designsystem.writing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.data.model.StrokeOrderStroke
import com.crazyfluff.shellfstudy.shared.designsystem.writing.WritingCanvas
import com.crazyfluff.shellfstudy.shared.designsystem.writing.WritingPracticeTestTags
import com.crazyfluff.shellfstudy.shared.designsystem.writing.WritingStroke
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class WritingCanvasTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val reference = listOf(StrokeOrderStroke(pathData = "M10,10L90,90", labelX = 5f, labelY = 5f))

    @Test
    fun `renders without crashing given completed and reference strokes`() {
        composeTestRule.setContent {
            WritingCanvas(
                completedStrokes = listOf(WritingStroke(points = listOf(Offset(0f, 0f), Offset(50f, 50f)))),
                currentStrokePoints = emptyList(),
                referenceStrokes = reference,
                showReference = true,
                onStrokeStart = {},
                onStrokeDrag = {},
                onStrokeEnd = {}
            )
        }

        composeTestRule.onNodeWithTag(WritingPracticeTestTags.CANVAS).assertIsDisplayed()
    }

    @Test
    fun `a drag gesture invokes start, drag, and end callbacks`() {
        var started = false
        var dragged = false
        var ended = false

        composeTestRule.setContent {
            WritingCanvas(
                completedStrokes = emptyList(),
                currentStrokePoints = emptyList(),
                referenceStrokes = reference,
                showReference = true,
                onStrokeStart = { started = true },
                onStrokeDrag = { dragged = true },
                onStrokeEnd = { ended = true }
            )
        }

        composeTestRule.onNodeWithTag(WritingPracticeTestTags.CANVAS).performTouchInput {
            down(Offset(10f, 10f))
            moveTo(Offset(60f, 60f))
            up()
        }

        assertThat(started).isTrue()
        assertThat(dragged).isTrue()
        assertThat(ended).isTrue()
    }
}
