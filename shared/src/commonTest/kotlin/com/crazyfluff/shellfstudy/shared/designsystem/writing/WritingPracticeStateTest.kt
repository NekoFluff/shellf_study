package com.crazyfluff.shellfstudy.shared.designsystem.writing

import androidx.compose.ui.geometry.Offset
import com.crazyfluff.shellfstudy.shared.designsystem.writing.WritingPracticeState
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class WritingPracticeStateTest {

    @Test
    fun `a drag with multiple points becomes one completed stroke`() {
        val state = WritingPracticeState()

        state.onDragStart(Offset(0f, 0f))
        state.onDrag(Offset(10f, 0f))
        state.onDrag(Offset(20f, 0f))
        state.onDragEnd()

        assertEquals(1, state.completedStrokes.size)
        assertEquals(listOf(Offset(0f, 0f), Offset(10f, 0f), Offset(20f, 0f)), state.completedStrokes.single().points)
        assertTrue(state.currentStrokePoints.isEmpty())
    }

    @Test
    fun `a tap with no drag is discarded rather than recorded as a stroke`() {
        val state = WritingPracticeState()

        state.onDragStart(Offset(5f, 5f))
        state.onDragEnd()

        assertTrue(state.completedStrokes.isEmpty())
        assertTrue(state.currentStrokePoints.isEmpty())
    }

    @Test
    fun `undoLast on an empty list is a no-op`() {
        val state = WritingPracticeState()

        state.undoLast()

        assertTrue(state.completedStrokes.isEmpty())
    }

    @Test
    fun `undoLast removes only the most recent stroke`() {
        val state = WritingPracticeState()
        state.onDragStart(Offset(0f, 0f))
        state.onDrag(Offset(1f, 1f))
        state.onDragEnd()
        state.onDragStart(Offset(0f, 0f))
        state.onDrag(Offset(2f, 2f))
        state.onDragEnd()

        state.undoLast()

        assertEquals(1, state.completedStrokes.size)
        assertEquals(Offset(1f, 1f), state.completedStrokes.single().points.last())
    }

    @Test
    fun `clear empties both completed and in-progress strokes`() {
        val state = WritingPracticeState()
        state.onDragStart(Offset(0f, 0f))
        state.onDrag(Offset(1f, 1f))
        state.onDragEnd()
        state.onDragStart(Offset(0f, 0f))
        state.onDrag(Offset(1f, 1f))

        state.clear()

        assertTrue(state.completedStrokes.isEmpty())
        assertTrue(state.currentStrokePoints.isEmpty())
    }
}
