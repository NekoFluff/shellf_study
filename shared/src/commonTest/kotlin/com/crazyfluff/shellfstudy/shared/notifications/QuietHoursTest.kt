package com.crazyfluff.shellfstudy.shared.notifications

import com.crazyfluff.shellfstudy.shared.notifications.QuietHours
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class QuietHoursTest {

    private val zone = TimeZone.UTC

    @Test
    fun `same-day window is quiet only between start and end`() {
        val start = 9
        val end = 17

        assertTrue(QuietHours.isQuietNow(10, start, end))
        assertTrue(QuietHours.isQuietNow(9, start, end))
        assertFalse(QuietHours.isQuietNow(17, start, end))
        assertFalse(QuietHours.isQuietNow(8, start, end))
    }

    @Test
    fun `overnight window wraps across midnight`() {
        val start = 22
        val end = 7

        assertTrue(QuietHours.isQuietNow(23, start, end))
        assertTrue(QuietHours.isQuietNow(3, start, end))
        assertTrue(QuietHours.isQuietNow(22, start, end))
        assertTrue(QuietHours.isQuietNow(6, start, end))
        assertFalse(QuietHours.isQuietNow(7, start, end))
        assertFalse(QuietHours.isQuietNow(12, start, end))
    }

    @Test
    fun `equal start and end means never quiet`() {
        assertFalse(QuietHours.isQuietNow(10, 9, 9))
    }

    @Test
    fun `nextEndInstant for overnight window in the pre-midnight half rolls to tomorrow morning`() {
        val start = 22
        val end = 7
        val now = LocalDateTime(2026, 8, 10, 23, 30)

        val result = QuietHours.nextEndInstant(now, zone, start, end)

        val expected = LocalDateTime(2026, 8, 11, end, 0).toInstant(zone)
        assertEquals(expected, result)
    }

    @Test
    fun `nextEndInstant for overnight window in the post-midnight half stays same calendar day`() {
        val start = 22
        val end = 7
        val now = LocalDateTime(2026, 8, 11, 3, 0)

        val result = QuietHours.nextEndInstant(now, zone, start, end)

        val expected = LocalDateTime(2026, 8, 11, end, 0).toInstant(zone)
        assertEquals(expected, result)
    }

    @Test
    fun `nextEndInstant for same-day window returns end later today`() {
        val start = 9
        val end = 17
        val now = LocalDateTime(2026, 8, 10, 10, 0)

        val result = QuietHours.nextEndInstant(now, zone, start, end)

        val expected = LocalDateTime(2026, 8, 10, end, 0).toInstant(zone)
        assertEquals(expected, result)
    }
}
