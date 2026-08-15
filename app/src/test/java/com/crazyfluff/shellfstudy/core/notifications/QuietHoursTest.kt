package com.crazyfluff.shellfstudy.core.notifications

import com.crazyfluff.shellfstudy.shared.notifications.QuietHours
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Test

class QuietHoursTest {

    private val zone = TimeZone.UTC

    @Test
    fun `same-day window is quiet only between start and end`() {
        val start = 9
        val end = 17

        assertThat(QuietHours.isQuietNow(10, start, end)).isTrue()
        assertThat(QuietHours.isQuietNow(9, start, end)).isTrue()
        assertThat(QuietHours.isQuietNow(17, start, end)).isFalse()
        assertThat(QuietHours.isQuietNow(8, start, end)).isFalse()
    }

    @Test
    fun `overnight window wraps across midnight`() {
        val start = 22
        val end = 7

        assertThat(QuietHours.isQuietNow(23, start, end)).isTrue()
        assertThat(QuietHours.isQuietNow(3, start, end)).isTrue()
        assertThat(QuietHours.isQuietNow(22, start, end)).isTrue()
        assertThat(QuietHours.isQuietNow(6, start, end)).isTrue()
        assertThat(QuietHours.isQuietNow(7, start, end)).isFalse()
        assertThat(QuietHours.isQuietNow(12, start, end)).isFalse()
    }

    @Test
    fun `equal start and end means never quiet`() {
        assertThat(QuietHours.isQuietNow(10, 9, 9)).isFalse()
    }

    @Test
    fun `nextEndInstant for overnight window in the pre-midnight half rolls to tomorrow morning`() {
        val start = 22
        val end = 7
        val now = LocalDateTime(2026, 8, 10, 23, 30)

        val result = QuietHours.nextEndInstant(now, zone, start, end)

        val expected = LocalDateTime(2026, 8, 11, end, 0).toInstant(zone)
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `nextEndInstant for overnight window in the post-midnight half stays same calendar day`() {
        val start = 22
        val end = 7
        val now = LocalDateTime(2026, 8, 11, 3, 0)

        val result = QuietHours.nextEndInstant(now, zone, start, end)

        val expected = LocalDateTime(2026, 8, 11, end, 0).toInstant(zone)
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `nextEndInstant for same-day window returns end later today`() {
        val start = 9
        val end = 17
        val now = LocalDateTime(2026, 8, 10, 10, 0)

        val result = QuietHours.nextEndInstant(now, zone, start, end)

        val expected = LocalDateTime(2026, 8, 10, end, 0).toInstant(zone)
        assertThat(result).isEqualTo(expected)
    }
}
