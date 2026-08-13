package com.crazyfluff.shellfstudy.core.designsystem.text

import androidx.compose.ui.text.AnnotatedString
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RomajiVisualTransformationTest {

    @Test
    fun `while editable, a trailing n with nothing after it yet stays unconverted`() {
        val transformed = RomajiVisualTransformation(isComplete = false)
            .filter(AnnotatedString("kousaten"))
        assertThat(transformed.text.text).isEqualTo("こうさてn")
    }

    @Test
    fun `once submitted, the same trailing n resolves to the ん grading actually checked against`() {
        val transformed = RomajiVisualTransformation(isComplete = true)
            .filter(AnnotatedString("kousaten"))
        assertThat(transformed.text.text).isEqualTo("こうさてん")
    }
}
