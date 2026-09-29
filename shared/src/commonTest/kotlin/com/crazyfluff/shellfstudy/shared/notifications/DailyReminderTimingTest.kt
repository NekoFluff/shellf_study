package com.crazyfluff.shellfstudy.shared.notifications

import com.crazyfluff.shellfstudy.shared.notifications.DailyReminderTiming
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class DailyReminderTimingTest {

    private val zone = TimeZone.UTC

    @Test
    fun `schedules later today when the target hour has not passed yet`() {
        val now = LocalDateTime(2026, 8, 10, 14, 0)

        val next = DailyReminderTiming.nextOccurrence(now, zone, hour = 20)

        val expected = LocalDateTime(2026, 8, 10, 20, 0).toInstant(zone)
        assertEquals(expected, next)
    }

    @Test
    fun `rolls to tomorrow when the target hour has already passed`() {
        val now = LocalDateTime(2026, 8, 10, 21, 0)

        val next = DailyReminderTiming.nextOccurrence(now, zone, hour = 20)

        val expected = LocalDateTime(2026, 8, 11, 20, 0).toInstant(zone)
        assertEquals(expected, next)
    }

    @Test
    fun `rolls to tomorrow when now is exactly the target hour`() {
        val now = LocalDateTime(2026, 8, 10, 20, 0)

        val next = DailyReminderTiming.nextOccurrence(now, zone, hour = 20)

        val expected = LocalDateTime(2026, 8, 11, 20, 0).toInstant(zone)
        assertEquals(expected, next)
    }
}
