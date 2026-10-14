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
    fun `забытая давняя отметка не мешает новому звонку`() {
        // Ловушка 2026-09-30: вечерний звук взвёл отметку, утренний звонок её не обновлял.
        val stale = now - ForeignAlarmRule.RINGING_MEMORY_MS - 1
        val since = ForeignAlarmRule.onNotification(true, now, stale)
        assertEquals(now, since)
        assertTrue(ForeignAlarmRule.isDismissal(since, now + 20_000L))
    }

    @Test
    fun `отметка из будущего после перевода часов заменяется`() {
        assertEquals(now, ForeignAlarmRule.onNotification(true, now, now + 60_000L))
    }

    @Test
    fun `первый чужой будильник поднимает экран`() {
        assertTrue(ForeignAlarmRule.raisesScreen(ourAlarmActive = false, challengePassedAt = null, nowMillis = now))
        assertTrue(ForeignAlarmRule.silencesForeign(false, challengePassedAt = null, nowMillis = now))
    }

    @Test
    fun `идущая наша тревога второй раз не поднимается, а чужой гасится`() {
        assertFalse(ForeignAlarmRule.raisesScreen(ourAlarmActive = true, challengePassedAt = null, nowMillis = now))
        assertTrue(ForeignAlarmRule.silencesForeign(ourAlarmActive = true, challengePassedAt = null, nowMillis = now))
    }

    @Test
    fun `в течение часа после испытания второй будильник экран не поднимает и звонит сам`() {
        val passed = now - 30 * 60_000L
        assertFalse(ForeignAlarmRule.raisesScreen(false, passed, now))
        assertFalse(ForeignAlarmRule.silencesForeign(false, passed, now))
    }

    @Test
    fun `через час после испытания будильник снова поднимает экран`() {
        val passed = now - ForeignAlarmRule.ONE_SCREEN_MS - 1
        assertTrue(ForeignAlarmRule.raisesScreen(false, passed, now))
        assertTrue(ForeignAlarmRule.silencesForeign(false, passed, now))
    }

    @Test
    fun `испытание из будущего после перевода часов не глушит будильник`() {
        assertTrue(ForeignAlarmRule.raisesScreen(false, challengePassedAt = now + 60_000L, nowMillis = now))
    }

    @Test
    fun `перевод часов назад не даёт ложного срабатывания`() {
        val since = ForeignAlarmRule.onNotification(true, now, null)
        assertFalse(ForeignAlarmRule.isDismissal(since, now - 60_000L))
    }
}
