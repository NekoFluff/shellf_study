package com.crazyfluff.shellfstudy.shared.sync

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [SyncActivity] is a process-wide mutable global, so its invariants are worth pinning: a flag stuck
 * on would tag every later frame `sync=inflight` and make the jank harness actively misleading —
 * worse than not having the tag — and a flag stuck off would silently hide the app's largest burst of
 * background work from the very measurements meant to find it.
 *
 * The counter exists specifically so an overlapping pass cannot clear the flag early; that is the
 * property these tests exist for.
 */
class SyncActivityTest {

    @Test
    fun `reports not syncing before any pass`() {
        assertFalse(SyncActivity.isSyncing.value)
    }

    @Test
    fun `reports syncing for the duration of a pass`() {
        SyncActivity.passStarted()
        try {
            assertTrue(SyncActivity.isSyncing.value)
        } finally {
            SyncActivity.passFinished()
        }
        assertFalse(SyncActivity.isSyncing.value)
    }

    /**
     * The reason this is a count rather than a boolean: the first of two overlapping passes to finish
     * must not report "not syncing" while the second is still running.
     */
    @Test
    fun `an overlapping pass keeps the flag set until the last one finishes`() {
        SyncActivity.passStarted()
        SyncActivity.passStarted()
        try {
            SyncActivity.passFinished()
            assertTrue(SyncActivity.isSyncing.value, "still one pass in flight")

            SyncActivity.passFinished()
            assertFalse(SyncActivity.isSyncing.value)
        } finally {
            // Guard against leaving the global set if an assertion above fails mid-test.
            SyncActivity.passFinished()
            SyncActivity.passFinished()
        }
    }

    /**
     * A mismatched pair — a double-finish from a bug elsewhere — must not drive the count negative,
     * which would leave the flag stuck off and hide sync from every later measurement.
     */
    @Test
    fun `an unmatched finish does not leave the flag stuck`() {
        SyncActivity.passFinished()
        assertFalse(SyncActivity.isSyncing.value)

        SyncActivity.passStarted()
        try {
            assertTrue(SyncActivity.isSyncing.value)
        } finally {
            SyncActivity.passFinished()
        }
        assertFalse(SyncActivity.isSyncing.value)
    }
}
