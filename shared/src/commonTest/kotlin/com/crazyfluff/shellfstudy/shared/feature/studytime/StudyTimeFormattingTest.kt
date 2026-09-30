package com.crazyfluff.shellfstudy.shared.feature.studytime

import kotlin.test.Test
import kotlin.test.assertEquals

class StudyTimeFormattingTest {
    @Test
    fun formatCount_groupsThousands() {
        assertEquals("0", formatCount(0))
        assertEquals("999", formatCount(999))
        assertEquals("1,000", formatCount(1_000))
        assertEquals("12,345,678", formatCount(12_345_678))
    }

    @Test
    fun formatCompactDuration_fitsAChartColumn() {
        val minute = 60_000L
        assertEquals("45m", formatCompactDuration(45 * minute))
        assertEquals("4.3h", formatCompactDuration((4 * 60 + 18) * minute))
        assertEquals("4h", formatCompactDuration(4 * 60 * minute))
        assertEquals("10h", formatCompactDuration((9 * 60 + 57) * minute))
        assertEquals("13h", formatCompactDuration((13 * 60 + 10) * minute))
    }
}
