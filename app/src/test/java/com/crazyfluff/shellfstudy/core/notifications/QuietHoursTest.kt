package com.crazyfluff.shellfstudy.core.notifications

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test

class QuietHoursTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    @Test
    fun `same-day window is quiet only between start and end`() {
        val start = LocalTime.of(9, 0)
        val end = LocalTime.of(17, 0)

        assertThat(QuietHours.isQuietNow(LocalTime.of(10, 0), start, end)).isTrue()
        assertThat(QuietHours.isQuietNow(LocalTime.of(9, 0), start, end)).isTrue()
        assertThat(QuietHours.isQuietNow(LocalTime.of(17, 0), start, end)).isFalse()
        assertThat(QuietHours.isQuietNow(LocalTime.of(8, 59), start, end)).isFalse()
    }

    @Test
    fun `overnight window wraps across midnight`() {
        val start = LocalTime.of(22, 0)
        val end = LocalTime.of(7, 0)

        assertThat(QuietHours.isQuietNow(LocalTime.of(23, 0), start, end)).isTrue()
        assertThat(QuietHours.isQuietNow(LocalTime.of(3, 0), start, end)).isTrue()
        assertThat(QuietHours.isQuietNow(LocalTime.of(22, 0), start, end)).isTrue()
        assertThat(QuietHours.isQuietNow(LocalTime.of(6, 59), start, end)).isTrue()
        assertThat(QuietHours.isQuietNow(LocalTime.of(7, 0), start, end)).isFalse()
        assertThat(QuietHours.isQuietNow(LocalTime.of(12, 0), start, end)).isFalse()
    }

    @Test
    fun `equal start and end means never quiet`() {
        val time = LocalTime.of(9, 0)
        assertThat(QuietHours.isQuietNow(LocalTime.of(10, 0), time, time)).isFalse()
    }

    @Test
    fun `nextEndInstant for overnight window in the pre-midnight half rolls to tomorrow morning`() {
        val start = LocalTime.of(22, 0)
        val end = LocalTime.of(7, 0)
        val now = LocalDateTime.of(LocalDate.of(2026, 8, 10), LocalTime.of(23, 30))

        val result = QuietHours.nextEndInstant(now, zone, start, end)

        val expected = LocalDateTime.of(LocalDate.of(2026, 8, 11), end).atZone(zone).toInstant()
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `nextEndInstant for overnight window in the post-midnight half stays same calendar day`() {
        val start = LocalTime.of(22, 0)
        val end = LocalTime.of(7, 0)
        val now = LocalDateTime.of(LocalDate.of(2026, 8, 11), LocalTime.of(3, 0))

        val result = QuietHours.nextEndInstant(now, zone, start, end)

        val expected = LocalDateTime.of(LocalDate.of(2026, 8, 11), end).atZone(zone).toInstant()
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `nextEndInstant for same-day window returns end later today`() {
        val start = LocalTime.of(9, 0)
        val end = LocalTime.of(17, 0)
        val now = LocalDateTime.of(LocalDate.of(2026, 8, 10), LocalTime.of(10, 0))

        val result = QuietHours.nextEndInstant(now, zone, start, end)

        val expected = LocalDateTime.of(LocalDate.of(2026, 8, 10), end).atZone(zone).toInstant()
        assertThat(result).isEqualTo(expected)
    }
}
