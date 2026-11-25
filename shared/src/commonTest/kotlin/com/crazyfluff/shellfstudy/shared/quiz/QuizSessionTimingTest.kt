package com.crazyfluff.shellfstudy.shared.quiz

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [QuizSessionTiming.onSegmentEnded] is the one hook study-time tracking records through, so a
 *  stretch must be reported exactly once when it ends, and nothing when none was running. */
class QuizSessionTimingTest {
    private var now = 1_000L
    private val ended = mutableListOf<Pair<Long, Long>>()
    private val timing = QuizSessionTiming(
        onResume = {},
        onPause = {},
        clock = { now },
        onSegmentEnded = { start, end -> ended += start to end }
    )

    @Test
    fun pause_reportsTheSegmentItEnded() {
        timing.resume()
        now = 4_000L
        timing.pause()

        assertEquals(listOf(1_000L to 4_000L), ended)
        assertEquals(3_000L, timing.elapsedMs)
    }

    @Test
    fun pauseWithNothingRunning_reportsNothing() {
        timing.pause()

        assertTrue(ended.isEmpty())
    }

    @Test
    fun secondPause_doesNotReportTheSameSegmentAgain() {
        timing.resume()
        now = 2_000L
        timing.pause()
        now = 3_000L
        timing.pause()

        assertEquals(1, ended.size)
    }

    @Test
    fun seededElapsedTime_isNotReportedAgain() {
        // A ViewModel resuming a persisted session seeds elapsedMs; only new time is a new segment.
        timing.elapsedMs = 60_000L
        timing.resume()
        now = 11_000L
        timing.pause()

        assertEquals(listOf(1_000L to 11_000L), ended)
        assertEquals(70_000L, timing.elapsedMs)
    }
}
