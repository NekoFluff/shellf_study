package com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Instant

/**
 * The three branches of the "Next review" cell, now decided from a passed-in instant rather than
 * wall-clock.
 *
 * This function read `Clock.System.now()` internally until `LocalClock` was introduced, which made
 * the past branch untestable in isolation: the fixture and the composition each read the clock
 * independently, so nothing pinned which side of the boundary a review sat on.
 */
class NextReviewTextTest {

    private val now = Instant.parse("2026-01-25T12:00:00.000000Z")

    @Test
    fun `no scheduled review reads as a dash`() {
        assertEquals("—", nextReviewText(nextReviewAt = null, now = now))
    }

    @Test
    fun `a review due at this instant or earlier reads as available now`() {
        assertEquals("Available now", nextReviewText(nextReviewAt = now, now = now))
        assertEquals(
            "Available now",
            nextReviewText(nextReviewAt = Instant.parse("2026-01-25T11:59:59.000000Z"), now = now)
        )
    }

    @Test
    fun `a review still in the future reads as its scheduled date`() {
        // Deliberately not asserted against the exact rendered string: formatDateTime resolves the
        // instant in the device timezone, so the literal differs by machine. Pinning the other two
        // branches to their exact copy is what makes "neither of those" a real assertion here.
        val text = nextReviewText(nextReviewAt = Instant.parse("2026-01-25T15:00:00.000000Z"), now = now)

        assertNotEquals("—", text)
        assertNotEquals("Available now", text)
    }
}
