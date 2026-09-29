package com.crazyfluff.shellfstudy.shared.designsystem.performance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * [jankStateKey] decides whether the tracker is told about a state change at all, so it is worth
 * pinning: a key that fails to change on a value change freezes the reported state, and a key that
 * changes spuriously re-enters the tracker on every recomposition — a per-frame cost inside a
 * per-frame harness.
 *
 * This exists because the first version keyed on `tags.size`. That looks like a cheap identity check
 * and is wrong: a screen reporting a fixed number of tags could never update any of them, so the
 * review screen reported `phase=loading` for its entire life while the UI showed an active question.
 * It survived review because different screens report different *counts*, so the screen tag appeared
 * to work and only the per-screen detail was frozen.
 */
class JankStateKeyTest {

    @Test
    fun `changes when a value changes, at constant tag count`() {
        val before = listOf("screen" to "review", "phase" to "loading", "answer" to "n/a")
        val after = listOf("screen" to "review", "phase" to "active", "answer" to "answering")

        assertEquals(before.size, after.size, "the regression only shows at equal tag counts")
        assertNotEquals(jankStateKey(before), jankStateKey(after))
    }

    /** The specific transition that was frozen: loading -> active on the review screen. */
    @Test
    fun `distinguishes every review phase at a fixed tag count`() {
        fun key(phase: String, answer: String, rank: String) = jankStateKey(
            listOf("screen" to "review", "phase" to phase, "answer" to answer, "rankChange" to rank)
        )

        val keys = setOf(
            key("loading", "n/a", "none"),
            key("active", "answering", "none"),
            key("active", "feedback_revealed", "none"),
            key("active", "feedback_revealed", "shown"),
            key("complete", "n/a", "none")
        )

        assertEquals(5, keys.size, "every distinct state must produce a distinct key")
    }

    /** An unchanged report must produce an identical key, or the tracker is re-entered needlessly. */
    @Test
    fun `is stable for identical tags in the same order`() {
        val tags = listOf("screen" to "dashboard", "sync" to "idle", "content" to "content")

        assertEquals(jankStateKey(tags), jankStateKey(tags.toList()))
    }

    /** Order differences must not collide: two different states cannot share a key. */
    @Test
    fun `distinguishes reordered tags`() {
        assertNotEquals(
            jankStateKey(listOf("a" to "1", "b" to "2")),
            jankStateKey(listOf("b" to "2", "a" to "1"))
        )
    }

    /**
     * Key and value boundaries must be unambiguous, or `a=1,b=2` and `a=1,b=2` style collisions
     * between differently-split pairs would report the wrong state.
     */
    @Test
    fun `does not collide across pair boundaries`() {
        assertNotEquals(
            jankStateKey(listOf("a" to "1", "b" to "2")),
            jankStateKey(listOf("a" to "1,b=2"))
        )
    }
}
