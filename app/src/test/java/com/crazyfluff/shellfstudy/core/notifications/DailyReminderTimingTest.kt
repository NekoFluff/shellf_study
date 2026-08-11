package com.crazyfluff.shellfstudy.core.notifications

import com.google.common.truth.Truth.assertThat
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Test

class DailyReminderTimingTest {

    @Test
    fun `schedules later today when the target hour has not passed yet`() {
        val now = ZonedDateTime.of(2026, 8, 10, 14, 0, 0, 0, ZoneOffset.UTC)

        val next = DailyReminderTiming.nextOccurrence(now, hour = 20)

        val expected = ZonedDateTime.of(2026, 8, 10, 20, 0, 0, 0, ZoneOffset.UTC).toInstant()
        assertThat(next).isEqualTo(expected)
    }

    @Test
    fun `rolls to tomorrow when the target hour has already passed`() {
        val now = ZonedDateTime.of(2026, 8, 10, 21, 0, 0, 0, ZoneOffset.UTC)

        val next = DailyReminderTiming.nextOccurrence(now, hour = 20)

        val expected = ZonedDateTime.of(2026, 8, 11, 20, 0, 0, 0, ZoneOffset.UTC).toInstant()
        assertThat(next).isEqualTo(expected)
    }

    @Test
    fun `rolls to tomorrow when now is exactly the target hour`() {
        val now = ZonedDateTime.of(2026, 8, 10, 20, 0, 0, 0, ZoneOffset.UTC)

        val next = DailyReminderTiming.nextOccurrence(now, hour = 20)

        val expected = ZonedDateTime.of(2026, 8, 11, 20, 0, 0, 0, ZoneOffset.UTC).toInstant()
        assertThat(next).isEqualTo(expected)
    }
}
