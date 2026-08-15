package com.crazyfluff.shellfstudy.core.designsystem.writing

import androidx.compose.ui.geometry.Offset
import com.crazyfluff.shellfstudy.shared.designsystem.writing.WritingPracticeState
import com.crazyfluff.shellfstudy.shared.designsystem.writing.WritingStroke
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WritingPracticeStateTest {

    @Test
    fun `a drag with multiple points becomes one completed stroke`() {
        val state = WritingPracticeState()

        state.onDragStart(Offset(0f, 0f))
        state.onDrag(Offset(10f, 0f))
        state.onDrag(Offset(20f, 0f))
        state.onDragEnd()

        assertThat(state.completedStrokes).hasSize(1)
        assertThat(state.completedStrokes.single().points)
            .containsExactly(Offset(0f, 0f), Offset(10f, 0f), Offset(20f, 0f))
            .inOrder()
        assertThat(state.currentStrokePoints).isEmpty()
    }

    @Test
    fun `a tap with no drag is discarded, not recorded as a stroke`() {
        val state = WritingPracticeState()

        state.onDragStart(Offset(5f, 5f))
        state.onDragEnd()

        assertThat(state.completedStrokes).isEmpty()
        assertThat(state.currentStrokePoints).isEmpty()
    }

    @Test
    fun `undoLast on an empty list is a no-op`() {
        val state = WritingPracticeState()

        state.undoLast()

        assertThat(state.completedStrokes).isEmpty()
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

        assertThat(state.completedStrokes).hasSize(1)
        assertThat(state.completedStrokes.single().points.last()).isEqualTo(Offset(1f, 1f))
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

        assertThat(state.completedStrokes).isEmpty()
        assertThat(state.currentStrokePoints).isEmpty()
    }
}
