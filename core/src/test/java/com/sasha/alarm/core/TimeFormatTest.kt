package com.sasha.alarm.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TimeFormatTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")

    @Test
    fun `время всегда с ведущими нулями`() {
        assertEquals("08:00", TimeFormat.clock(8, 0))
        assertEquals("00:05", TimeFormat.clock(0, 5))
        assertEquals("23:59", TimeFormat.clock(23, 59))
    }

    @Test
    fun `длительность показывается как минуты и секунды`() {
        assertEquals("0:00", TimeFormat.duration(0L))
        assertEquals("0:07", TimeFormat.duration(7_500L))
        assertEquals("4:18", TimeFormat.duration((4 * 60 + 18) * 1000L))
        assertEquals("12:00", TimeFormat.duration(12 * 60 * 1000L))
    }

    @Test
    fun `отрицательная длительность обнуляется`() {
        assertEquals("0:00", TimeFormat.duration(-5_000L))
    }

    @Test
    fun `метка времени переводится в часы и минуты часового пояса`() {
        val millis = LocalDateTime.of(2026, 8, 14, 7, 5).atZone(zone).toInstant().toEpochMilli()
        assertEquals("07:05", TimeFormat.clockAt(millis, zone))
    }
}
