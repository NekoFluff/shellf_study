package com.crazyfluff.shellfstudy.core.designsystem.text

import androidx.compose.foundation.text.input.TextFieldState
import com.crazyfluff.shellfstudy.shared.designsystem.text.RomajiOutputTransformation
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RomajiOutputTransformationTest {

    private fun transform(raw: String, isComplete: Boolean): String {
        val state = TextFieldState(raw)
        state.edit { with(RomajiOutputTransformation(isComplete)) { transformOutput() } }
        return state.text.toString()
    }

    @Test
    fun `while editable, a trailing n with nothing after it yet stays unconverted`() {
        assertThat(transform("kousaten", isComplete = false)).isEqualTo("こうさてn")
    }

    @Test
    fun `once submitted, the same trailing n resolves to the ん grading actually checked against`() {
        assertThat(transform("kousaten", isComplete = true)).isEqualTo("こうさてん")
    }
}
