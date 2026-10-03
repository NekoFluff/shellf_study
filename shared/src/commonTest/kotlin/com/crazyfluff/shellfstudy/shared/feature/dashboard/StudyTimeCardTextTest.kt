package com.crazyfluff.shellfstudy.shared.feature.dashboard

import com.crazyfluff.shellfstudy.shared.data.studytime.StudyPace
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeOverview
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeSplit
import kotlin.test.Test
import kotlin.test.assertEquals

class StudyTimeCardTextTest {
    @Test
    fun todaySuffix_namesTheGoal() {
        assertEquals("of 30m today", todaySuffix(overview(todayMs = 21 * MINUTE, goalMs = 30 * MINUTE)))
    }

    @Test
    fun todaySuffix_withoutAGoal_isJustToday() {
        assertEquals("today", todaySuffix(overview(todayMs = 5 * MINUTE, goalMs = 0L)))
    }

    @Test
    fun todaySuffix_beforeAnySession_invitesOne() {
        assertEquals(
            "Starts with your next session",
            todaySuffix(overview(todayMs = 0L, goalMs = 30 * MINUTE, hasAnyData = false))
        )
    }

    private fun overview(todayMs: Long, goalMs: Long, hasAnyData: Boolean = true) = StudyTimeOverview(
        today = StudyTimeSplit(reviewMs = todayMs),
        goalMs = goalMs,
        lastSevenDays = emptyList(),
        pace = StudyPace(),
        hasAnyData = hasAnyData
    )

    private companion object {
        const val MINUTE = 60_000L
    }
}
