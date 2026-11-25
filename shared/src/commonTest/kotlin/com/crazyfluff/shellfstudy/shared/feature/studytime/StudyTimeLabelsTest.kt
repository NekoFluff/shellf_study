package com.crazyfluff.shellfstudy.shared.feature.studytime

import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StudyTimeLabelsTest {
    @Test
    fun periodChange_namesTheMetricAndTheEarlierTotal() {
        assertEquals(
            "▼ 25% less study time than the previous 7 days (4h)",
            periodChangeText(change = -0.25, previousTotalMs = 4 * HOUR, window = StudyTimeWindow.WEEK)
        )
        assertEquals(
            "▲ 50% more study time than the previous 30 days (2h)",
            periodChangeText(change = 0.5, previousTotalMs = 2 * HOUR, window = StudyTimeWindow.MONTH)
        )
    }

    @Test
    fun pace_isTimePerItemAndAbsentWithoutItems() {
        assertEquals("12.0s per review", paceLabel(ms = 120_000L, count = 10, noun = "review"))
        assertEquals("2m 30s per lesson", paceLabel(ms = 300_000L, count = 2, noun = "lesson"))
        assertNull(paceLabel(ms = 60_000L, count = 0, noun = "review"))
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}
