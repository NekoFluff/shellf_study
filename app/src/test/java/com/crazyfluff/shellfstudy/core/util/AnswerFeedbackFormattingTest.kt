package com.crazyfluff.shellfstudy.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AnswerFeedbackFormattingTest {

    @Test
    fun `short list is shown in full and reports no more to expand`() {
        val display = formatAnswerList("one, two, three")
        assertThat(display.text).isEqualTo("one, two, three")
        assertThat(display.hasMore).isFalse()
    }

    @Test
    fun `list at the cap is shown in full`() {
        val display = formatAnswerList("a, b, c")
        assertThat(display.text).isEqualTo("a, b, c")
        assertThat(display.hasMore).isFalse()
    }

    @Test
    fun `list over the cap is truncated with a count of the rest`() {
        val display = formatAnswerList("a, b, c, d, e")
        assertThat(display.text).isEqualTo("a, b, c +2 more")
        assertThat(display.hasMore).isTrue()
    }

    @Test
    fun `expanded shows the full list even over the cap`() {
        val display = formatAnswerList("a, b, c, d, e", expanded = true)
        assertThat(display.text).isEqualTo("a, b, c, d, e")
        // Still reports hasMore so a caller can keep offering to collapse it back down.
        assertThat(display.hasMore).isTrue()
    }

    @Test
    fun `single answer is unaffected`() {
        val display = formatAnswerList("Water")
        assertThat(display.text).isEqualTo("Water")
        assertThat(display.hasMore).isFalse()
    }
}
