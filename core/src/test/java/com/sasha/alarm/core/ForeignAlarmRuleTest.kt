package com.sasha.alarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForeignAlarmRuleTest {

    private val now = 1_000_000L

    @Test
    fun `первое звонящее уведомление запоминает момент`() {
        assertEquals(now, ForeignAlarmRule.onNotification(ringing = true, nowMillis = now, since = null))
    }

    @Test
    fun `повторные обновления не сдвигают момент начала звонка`() {
        val since = ForeignAlarmRule.onNotification(true, now, null)
        assertEquals(now, ForeignAlarmRule.onNotification(true, now + 30_000L, since))
    }

    @Test
    fun `уведомление перестало быть звонящим - факт забывается`() {
        val since = ForeignAlarmRule.onNotification(true, now, null)
        assertNull(ForeignAlarmRule.onNotification(false, now + 5_000L, since))
    }

    @Test
    fun `выключение звонящего будильника - это наш сигнал`() {
        val since = ForeignAlarmRule.onNotification(true, now, null)
        assertTrue(ForeignAlarmRule.isDismissal(since, now + 20_000L))
    }

    @Test
    fun `остановка отслеживания сна без звонка сигналом не является`() {
        // Вечерний сценарий: владелец просто выключил трекинг, будильник не звонил.
        assertFalse(ForeignAlarmRule.isDismissal(since = null, nowMillis = now))
    }

    @Test
    fun `давний звонок выключением не считается`() {
        val since = ForeignAlarmRule.onNotification(true, now, null)
        assertFalse(ForeignAlarmRule.isDismissal(since, now + ForeignAlarmRule.RINGING_MEMORY_MS + 1))
    }

    @Test
    fun `перевод часов назад не даёт ложного срабатывания`() {
        val since = ForeignAlarmRule.onNotification(true, now, null)
        assertFalse(ForeignAlarmRule.isDismissal(since, now - 60_000L))
    }
}
