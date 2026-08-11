package com.crazyfluff.shellfstudy.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CloseEnoughMatcherTest {

    @Test
    fun `exact match is reported as exact`() {
        val result = CloseEnoughMatcher.match("water", listOf("Water"))
        assertThat(result.isMatch).isTrue()
        assertThat(result.isExact).isTrue()
        assertThat(result.matchedCandidate).isEqualTo("Water")
    }

    @Test
    fun `case and surrounding whitespace are ignored for an exact match`() {
        val result = CloseEnoughMatcher.match("  WATER  ", listOf("water"))
        assertThat(result.isMatch).isTrue()
        assertThat(result.isExact).isTrue()
    }

    @Test
    fun `short candidates require an exact match`() {
        // "to" (length 2) allows 0 edits — "ot" (a transposition, 1 edit) must not match.
        val result = CloseEnoughMatcher.match("ot", listOf("to"))
        assertThat(result.isMatch).isFalse()
    }

    @Test
    fun `one-letter typo on a mid-length word is accepted as a near match`() {
        // "guide" (length 5) allows 1 edit.
        val result = CloseEnoughMatcher.match("guode", listOf("guide"))
        assertThat(result.isMatch).isTrue()
        assertThat(result.isExact).isFalse()
        assertThat(result.matchedCandidate).isEqualTo("guide")
    }

    @Test
    fun `adjacent transposition counts as a single edit`() {
        // "guide" allows 1 edit — "gudie" (i/d swapped) is a single transposition.
        val result = CloseEnoughMatcher.match("gudie", listOf("guide"))
        assertThat(result.isMatch).isTrue()
        assertThat(result.isExact).isFalse()
    }

    @Test
    fun `two-letter typo on a mid-length word exceeds the threshold`() {
        // "guide" only allows 1 edit — two substitutions must not match.
        val result = CloseEnoughMatcher.match("gaids", listOf("guide"))
        assertThat(result.isMatch).isFalse()
    }

    @Test
    fun `longer words tolerate more edits`() {
        // "government" (length 10) allows 10/7 + 2 = 3 edits.
        val result = CloseEnoughMatcher.match("govermment", listOf("government"))
        assertThat(result.isMatch).isTrue()
    }

    @Test
    fun `picks the closest candidate among several`() {
        val result = CloseEnoughMatcher.match("wager", listOf("eager", "water", "wagers"))
        assertThat(result.isMatch).isTrue()
        // "wager" vs "water": 2 edits: vs "eager": 1 edit; vs "wagers": 1 edit (insertion) —
        // the closest (lowest-distance) candidate wins.
        assertThat(result.matchedCandidate).isAnyOf("eager", "wagers")
    }

    @Test
    fun `blank answer never matches`() {
        val result = CloseEnoughMatcher.match("   ", listOf("water"))
        assertThat(result.isMatch).isFalse()
    }

    @Test
    fun `empty candidate list never matches`() {
        val result = CloseEnoughMatcher.match("water", emptyList())
        assertThat(result.isMatch).isFalse()
    }
}
