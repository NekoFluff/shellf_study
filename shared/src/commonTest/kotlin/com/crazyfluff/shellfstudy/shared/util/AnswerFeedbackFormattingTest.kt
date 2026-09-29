package com.crazyfluff.shellfstudy.shared.util

import com.crazyfluff.shellfstudy.shared.util.formatAnswerList
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class AnswerFeedbackFormattingTest {

    @Test
    fun `short list is shown in full and reports no more to expand`() {
        val display = formatAnswerList("one, two, three")
        assertEquals("one, two, three", display.text)
        assertFalse(display.hasMore)
    }

    @Test
    fun `list at the cap is shown in full`() {
        val display = formatAnswerList("a, b, c")
        assertEquals("a, b, c", display.text)
        assertFalse(display.hasMore)
    }

    @Test
    fun `list over the cap is truncated with a count of the rest`() {
        val display = formatAnswerList("a, b, c, d, e")
        assertEquals("a, b, c +2 more", display.text)
        assertTrue(display.hasMore)
    }

    @Test
    fun `expanded shows the full list even over the cap`() {
        val display = formatAnswerList("a, b, c, d, e", expanded = true)
        assertEquals("a, b, c, d, e", display.text)
        // Still reports hasMore so a caller can keep offering to collapse it back down.
        assertTrue(display.hasMore)
    }

    @Test
    fun `single answer is unaffected`() {
        val display = formatAnswerList("Water")
        assertEquals("Water", display.text)
        assertFalse(display.hasMore)
    }
}
