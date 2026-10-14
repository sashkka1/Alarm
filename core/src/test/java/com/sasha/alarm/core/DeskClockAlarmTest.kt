package com.sasha.alarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeskClockAlarmTest {

    private val now = 1_000_000L

    @Test
    fun `звонок будильника — канал будильника и служба переднего плана`() {
        assertTrue(DeskClockAlarm.isRinging("channel_id_deskclock_alarm", foregroundService = true))
    }

    @Test
    fun `таймер наш экран не поднимает`() {
        assertFalse(DeskClockAlarm.isRinging("channel_id_deskclock_timer", foregroundService = true))
    }

    @Test
    fun `уведомление будильника без службы — не звонок`() {
        assertFalse(DeskClockAlarm.isRinging("channel_id_deskclock_alarm", foregroundService = false))
    }

    @Test
    fun `отложенный будильник — не звонок`() {
        assertFalse(DeskClockAlarm.isRinging("channel_id_deskclock_snooze", foregroundService = true))
    }

    @Test
    fun `кнопка Стоп находится, Повтор — нет`() {
        assertEquals(0, DeskClockAlarm.stopActionIndex(listOf("Стоп", "Повтор")))
        assertEquals(1, DeskClockAlarm.stopActionIndex(listOf("Snooze", "Stop")))
        assertNull(DeskClockAlarm.stopActionIndex(listOf("Повтор")))
        assertNull(DeskClockAlarm.stopActionIndex(listOf(null)))
    }

    @Test
    fun `звук при звонке часов — их звук`() {
        assertTrue(DeskClockAlarm.isItsAudio(ringing = true, ringEndedAt = null, nowMillis = now))
    }

    @Test
    fun `звук сразу после звонка часов — ещё их`() {
        assertTrue(DeskClockAlarm.isItsAudio(false, ringEndedAt = now - 3_000L, nowMillis = now))
    }

    @Test
    fun `звук спустя окно — уже не часы`() {
        assertFalse(DeskClockAlarm.isItsAudio(false, now - DeskClockAlarm.AUDIO_TAIL_MS - 1, now))
        assertFalse(DeskClockAlarm.isItsAudio(false, ringEndedAt = null, nowMillis = now))
    }

    @Test
    fun `часы, переведённые назад, окно не продлевают`() {
        assertFalse(DeskClockAlarm.isItsAudio(false, ringEndedAt = now + 60_000L, nowMillis = now))
    }
}
