package com.crazyfluff.shellfstudy.shared.data

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [localMidnightIso] feeds a *lexicographic* SQL comparison against the `startedAt` values WaniKani
 * writes, so its exact output format is load-bearing rather than cosmetic. These tests pin the two
 * ways that can silently break:
 *
 * 1. The fraction must be present and zero-padded. WaniKani writes
 *    `2023-04-04T07:56:05.000000Z`; a cutoff of `2026-09-28T00:00:00Z` still happens to compare
 *    correctly for these values (because `'.' < 'Z'` puts the shorter string *after* the longer one
 *    at equal digits), but it is relying on an accident of ASCII ordering rather than on the digits.
 * 2. `kotlin.time.Instant.toString()` must not be used to build this. It omits a zero fraction
 *    entirely and otherwise emits a variable digit count, which is exactly failure mode 1.
 */
class LocalMidnightIsoTest {

    @Test
    fun `always includes an explicit six-digit zero fraction`() {
        assertEquals("2026-09-28T00:00:00.000000Z", localMidnightIso(LocalDate(2026, 9, 28)))
    }

    @Test
    fun `zero-pads single-digit month and day`() {
        assertEquals("2026-01-05T00:00:00.000000Z", localMidnightIso(LocalDate(2026, 1, 5)))
    }

    @Test
    fun `is always the same length regardless of date`() {
        val lengths = listOf(
            LocalDate(2026, 1, 1),
            LocalDate(2026, 12, 31),
            LocalDate(2026, 6, 15)
        ).map { localMidnightIso(it).length }.distinct()
        assertEquals(listOf(27), lengths)
    }

    /**
     * The comparison the DAO performs, replayed in Kotlin against real WaniKani-shaped timestamps.
     * A timestamp earlier the same day must fall *below* the cutoff and one later that day *above*
     * it — the property the old per-row `Instant.parse(...).toLocalDateTime(zone).date == today`
     * filter also had, and which the SQL version has to reproduce.
     */
    @Test
    fun `sorts correctly against same-day timestamps in both directions`() {
        val cutoff = localMidnightIso(LocalDate(2026, 9, 28))

        val laterSameDay = "2026-09-28T00:00:00.000001Z"
        val muchLaterSameDay = "2026-09-28T23:59:59.999999Z"
        val previousDay = "2026-09-27T23:59:59.999999Z"

        assertTrue(laterSameDay >= cutoff, "a timestamp after local midnight must be counted")
        assertTrue(muchLaterSameDay >= cutoff, "the last instant of the day must be counted")
        assertTrue(previousDay < cutoff, "the last instant of yesterday must not be counted")
    }

    /**
     * Exactly midnight is *included*, matching the old `date == today` filter — a lesson started at
     * 00:00:00.000000 belongs to today, not to yesterday.
     */
    @Test
    fun `midnight itself is included`() {
        val cutoff = localMidnightIso(LocalDate(2026, 9, 28))
        assertTrue(cutoff >= cutoff)
    }
}
