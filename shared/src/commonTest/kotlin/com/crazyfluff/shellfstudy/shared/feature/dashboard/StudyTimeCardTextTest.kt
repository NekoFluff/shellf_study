package com.crazyfluff.shellfstudy.shared.feature.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StudyTimeCardTextTest {
    @Test
    fun levelTimeThisLevel_formatsRecordedTimeAndIsAbsentWithoutAny() {
        assertEquals("4h 24m this level", levelTimeThisLevel((4 * 60 + 24) * 60_000L))
        assertNull(levelTimeThisLevel(0L))
        assertNull(levelTimeThisLevel(null))
    }
}
