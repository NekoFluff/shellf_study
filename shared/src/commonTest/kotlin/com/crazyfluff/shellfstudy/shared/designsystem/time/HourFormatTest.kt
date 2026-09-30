package com.crazyfluff.shellfstudy.shared.designsystem.time

import kotlin.test.Test
import kotlin.test.assertEquals

class HourFormatTest {
    @Test
    fun twelveHourClock_labelsMidnightAndNoonAsTwelve() {
        assertEquals("12:00 AM", formatHour(0, is24h = false))
        assertEquals("12:00 PM", formatHour(12, is24h = false))
        assertEquals("11:00 PM", formatHour(23, is24h = false))
    }

    @Test
    fun twentyFourHourClock_zeroPads() {
        assertEquals("00:00", formatHour(0, is24h = true))
        assertEquals("09:00", formatHour(9, is24h = true))
        assertEquals("23:00", formatHour(23, is24h = true))
    }

    @Test
    fun hoursWrapInBothDirections() {
        assertEquals("11:00 PM", formatHour(-1, is24h = false))
        assertEquals("00:00", formatHour(24, is24h = true))
    }

    @Test
    fun shortForm_dropsTheMinutesAndSuffix() {
        assertEquals("8", formatHourShort(20, is24h = false))
        assertEquals("12", formatHourShort(0, is24h = false))
        assertEquals("20", formatHourShort(20, is24h = true))
    }
}
