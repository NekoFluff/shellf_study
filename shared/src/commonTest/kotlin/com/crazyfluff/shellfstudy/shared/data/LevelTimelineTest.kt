package com.crazyfluff.shellfstudy.shared.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LevelTimelineTest {

    @Test
    fun emptyProgressions_returnsEmpty() {
        val result = buildTimeline(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun singleProgression_returnsOnePointAtDayZero() {
        val result = buildTimeline(listOf(1 to "2020-01-01T00:00:00Z"))
        assertEquals(1, result.size)
        assertEquals(0, result.first().daysSinceStart)
        assertEquals(1, result.first().level)
    }

    @Test
    fun multipleProgressions_daysCalculatedFromFirst() {
        val progressions = listOf(
            1 to "2020-01-01T00:00:00Z",
            2 to "2020-01-08T00:00:00Z",   // 7 days later
            3 to "2020-01-15T00:00:00Z"    // 14 days later
        )
        val result = buildTimeline(progressions)
        assertEquals(3, result.size)
        assertEquals(listOf(0, 7, 14), result.map { it.daysSinceStart })
        assertEquals(listOf(1, 2, 3), result.map { it.level })
    }

    @Test
    fun invalidIntermediateTimestamp_skipped() {
        val progressions = listOf(
            1 to "2020-01-01T00:00:00Z",
            2 to "not-a-date",
            3 to "2020-01-15T00:00:00Z"
        )
        val result = buildTimeline(progressions)
        assertEquals(2, result.size)
        assertEquals(listOf(1, 3), result.map { it.level })
    }

    @Test
    fun invalidFirstTimestamp_returnsEmpty() {
        val result = buildTimeline(listOf(1 to "invalid"))
        assertTrue(result.isEmpty())
    }
}
