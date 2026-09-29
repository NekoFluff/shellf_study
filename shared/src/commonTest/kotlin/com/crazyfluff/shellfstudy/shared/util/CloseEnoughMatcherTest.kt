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
}
