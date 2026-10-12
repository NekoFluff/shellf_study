package com.crazyfluff.shellfstudy.shared.util

import com.crazyfluff.shellfstudy.shared.util.CloseEnoughMatcher
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class CloseEnoughMatcherTest {

    @Test
    fun `exact match is reported as exact`() {
        val result = CloseEnoughMatcher.match("water", listOf("Water"))
        assertTrue(result.isMatch)
        assertTrue(result.isExact)
        assertEquals("Water", result.matchedCandidate)
    }

    @Test
    fun `case and surrounding whitespace are ignored for an exact match`() {
        val result = CloseEnoughMatcher.match("  WATER  ", listOf("water"))
        assertTrue(result.isMatch)
        assertTrue(result.isExact)
    }

    @Test
    fun `short candidates require an exact match`() {
        // "to" (length 2) allows 0 edits — "ot" (a transposition, 1 edit) must not match.
        val result = CloseEnoughMatcher.match("ot", listOf("to"))
        assertFalse(result.isMatch)
    }

    @Test
    fun `one-letter typo on a mid-length word is accepted as a near match`() {
        // "guide" (length 5) allows 1 edit.
        val result = CloseEnoughMatcher.match("guode", listOf("guide"))
        assertTrue(result.isMatch)
        assertFalse(result.isExact)
        assertEquals("guide", result.matchedCandidate)
    }

    @Test
    fun `adjacent transposition counts as a single edit`() {
        // "guide" allows 1 edit — "gudie" (i/d swapped) is a single transposition.
        val result = CloseEnoughMatcher.match("gudie", listOf("guide"))
        assertTrue(result.isMatch)
        assertFalse(result.isExact)
    }

    @Test
    fun `two-letter typo on a mid-length word exceeds the threshold`() {
        // "guide" only allows 1 edit — two substitutions must not match.
        val result = CloseEnoughMatcher.match("gaids", listOf("guide"))
        assertFalse(result.isMatch)
    }

    @Test
    fun `longer words tolerate more edits`() {
        // "government" (length 10) allows 10/7 + 2 = 3 edits.
        val result = CloseEnoughMatcher.match("govermment", listOf("government"))
        assertTrue(result.isMatch)
    }

    @Test
    fun `picks the closest candidate among several`() {
        val result = CloseEnoughMatcher.match("wager", listOf("eager", "water", "wagers"))
        assertTrue(result.isMatch)
        // "wager" vs "water": 2 edits: vs "eager": 1 edit; vs "wagers": 1 edit (insertion) —
        // the closest (lowest-distance) candidate wins.
        assertTrue(result.matchedCandidate in listOf("eager", "wagers"))
    }

    @Test
    fun `blank answer never matches`() {
        val result = CloseEnoughMatcher.match("   ", listOf("water"))
        assertFalse(result.isMatch)
    }

    @Test
    fun `empty candidate list never matches`() {
        val result = CloseEnoughMatcher.match("water", emptyList())
        assertFalse(result.isMatch)
    }

    @Test
    fun `allowCloseEnough false rejects a typo that would otherwise be within threshold`() {
        val result = CloseEnoughMatcher.match("guode", listOf("guide"), allowCloseEnough = false)
        assertFalse(result.isMatch)
    }

    @Test
    fun `allowCloseEnough false still accepts an exact match after case and whitespace cleanup`() {
        val result = CloseEnoughMatcher.match("  WATER  ", listOf("water"), allowCloseEnough = false)
        assertTrue(result.isMatch)
        assertTrue(result.isExact)
    }

    @Test
    fun `punctuation and hyphens are cleaned up rather than spent as typos`() {
        assertTrue(CloseEnoughMatcher.match("oclock", listOf("o'clock")).isExact)
        assertTrue(CloseEnoughMatcher.match("well known", listOf("well-known")).isExact)
        assertTrue(CloseEnoughMatcher.match("10th.", listOf("10th")).isExact)
    }

    @Test
    fun `keyboard variants of quotes dashes and digits match the plain ones exactly`() {
        assertTrue(CloseEnoughMatcher.match("o\u2019clock", listOf("o'clock")).isExact)
        assertTrue(CloseEnoughMatcher.match("well\u2013known", listOf("well-known")).isExact)
        assertTrue(CloseEnoughMatcher.match("\uFF14 days", listOf("4 days")).isExact)
    }

    @Test
    fun `a different number is never a typo`() {
        // "5 days" (length 6) allows 2 edits, and "4 days" is only 1 away.
        assertFalse(CloseEnoughMatcher.match("4 days", listOf("5 days")).isMatch)
    }

    @Test
    fun `a typo around the right number is still forgiven`() {
        val result = CloseEnoughMatcher.match("10 dyas", listOf("10 days"))
        assertTrue(result.isMatch)
        assertFalse(result.isExact)
    }

    @Test
    fun `a digit answer never fuzzy-matches a spelled-out candidate`() {
        assertFalse(CloseEnoughMatcher.match("4", listOf("four")).isMatch)
    }

    @Test
    fun `an exact rejected answer never matches even within typo distance of an accepted one`() {
        // "ascend" (length 6) allows 2 edits, and "descend" is exactly 2 away.
        assertFalse(CloseEnoughMatcher.match("descend", listOf("ascend"), rejected = listOf("descend")).isMatch)
    }

    @Test
    fun `a typo closer to a rejected answer than to an accepted one is refused`() {
        assertFalse(CloseEnoughMatcher.match("decsend", listOf("ascend"), rejected = listOf("descend")).isMatch)
    }

    @Test
    fun `a typo as close to a rejected answer as to an accepted one is refused`() {
        assertFalse(CloseEnoughMatcher.match("cars", listOf("cart"), rejected = listOf("card")).isMatch)
    }

    @Test
    fun `a typo closer to the accepted answer is forgiven despite a rejected one`() {
        val result = CloseEnoughMatcher.match("gudie", listOf("guide"), rejected = listOf("glide"))
        assertTrue(result.isMatch)
        assertEquals("guide", result.matchedCandidate)
    }

    @Test
    fun `an exact accepted answer wins even if it is also rejected`() {
        assertTrue(CloseEnoughMatcher.match("water", listOf("water"), rejected = listOf("water")).isExact)
    }
}
