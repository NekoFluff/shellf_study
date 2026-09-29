package com.crazyfluff.shellfstudy.shared.designsystem.text

import androidx.compose.foundation.text.input.TextFieldState
import com.crazyfluff.shellfstudy.shared.designsystem.text.RomajiOutputTransformation
import kotlin.test.Test
import kotlin.test.assertEquals

class RomajiOutputTransformationTest {

    private fun transform(raw: String, isComplete: Boolean): String {
        val state = TextFieldState(raw)
        state.edit { with(RomajiOutputTransformation(isComplete)) { transformOutput() } }
        return state.text.toString()
    }

    @Test
    fun `while editable a trailing n with nothing after it yet stays unconverted`() {
        assertEquals("こうさてn", transform("kousaten", isComplete = false))
    }

    @Test
    fun `once submitted the same trailing n resolves to the ん grading actually checked against`() {
        assertEquals("こうさてん", transform("kousaten", isComplete = true))
    }
}
