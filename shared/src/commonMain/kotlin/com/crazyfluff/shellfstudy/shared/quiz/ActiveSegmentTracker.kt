package com.crazyfluff.shellfstudy.shared.quiz

import kotlin.time.Clock

/**
 * Tracks the wall-clock time a session is actively being viewed, accumulating time only while a
 * segment is running. Mirrors the sessionActiveElapsedMs / sessionActiveSegmentStartMs fields in
 * the session UiStates so the timer composable can read them.
 *
 * Usage: call [resume] when the user opens / returns to the session, [pause] when they leave or
 * the app backgrounds. [currentElapsedMs] gives the live total at any point.
 */
class ActiveSegmentTracker(private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() }) {
    var elapsedMs: Long = 0L
    var segmentStartMs: Long? = null

    /** Starts a new segment. Returns the start timestamp if a new segment began, null if already running. */
    fun resume(): Long? {
        if (segmentStartMs != null) return null
        val now = clock()
        segmentStartMs = now
        return now
    }

    /** Folds the current segment into [elapsedMs]. Returns the new total, or null if no segment was running. */
    fun pause(): Long? {
        val startedAt = segmentStartMs ?: return null
        elapsedMs += clock() - startedAt
        segmentStartMs = null
        return elapsedMs
    }

    fun currentElapsedMs(nowMs: Long = clock()): Long =
        elapsedMs + (segmentStartMs?.let { nowMs - it } ?: 0L)
}
