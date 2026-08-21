package com.sasha.alarm.core

import java.time.Instant
import java.time.ZoneId

object TimeFormat {

    /** «08:00» — единственный формат времени в приложении. */
    fun clock(hour: Int, minute: Int): String =
        buildString {
            if (hour < 10) append('0')
            append(hour)
            append(':')
            if (minute < 10) append('0')
            append(minute)
        }

    fun clockAt(millis: Long, zone: ZoneId): String {
        val t = Instant.ofEpochMilli(millis).atZone(zone)
        return clock(t.hour, t.minute)
    }

    /** Длительность в виде «4:18». Отрицательные значения обнуляются. */
    fun duration(millis: Long): String {
        val totalSeconds = (millis.coerceAtLeast(0L)) / 1000L
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return buildString {
            append(minutes)
            append(':')
            if (seconds < 10) append('0')
            append(seconds)
        }
    }
}
