package com.sasha.alarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncHealthTest {

    private val hour = 60L * 60 * 1000
    private val now = 1_000_000_000_000L

    @Test
    fun `свежая передача тревоги не поднимает`() {
        val state = SyncHealth.State(lastAttemptAt = now - hour, lastOkAt = now - hour, lastAttemptOk = true)
        assertEquals(SyncHealth.Verdict.FINE, SyncHealth.verdict(state, now))
    }

    @Test
    fun `провалившаяся попытка видна сразу, ждать суток не надо`() {
        val state = SyncHealth.State(lastAttemptAt = now - hour, lastOkAt = now - hour * 2, lastAttemptOk = false)
        assertEquals(SyncHealth.Verdict.FAILED, SyncHealth.verdict(state, now))
    }

    @Test
    fun `молчание дольше порога — тоже отказ, даже если последняя попытка была удачной`() {
        // Ровно тот случай, ради которого правило написано: 31.08.2026 попытки не было
        // вовсе, и признак «последняя попытка провалилась» промолчал бы.
        val state = SyncHealth.State(lastAttemptAt = now - hour * 30, lastOkAt = now - hour * 30, lastAttemptOk = true)
        assertEquals(SyncHealth.Verdict.STALE, SyncHealth.verdict(state, now))
    }

    @Test
    fun `сутки с небольшим тревоги не поднимают — расписание дрожит на минуты`() {
        val state = SyncHealth.State(lastAttemptAt = now - hour * 25, lastOkAt = now - hour * 25, lastAttemptOk = true)
        assertEquals(SyncHealth.Verdict.FINE, SyncHealth.verdict(state, now))
    }

    @Test
    fun `ровно на пороге уже тревога`() {
        val state = SyncHealth.State(
            lastAttemptAt = now - SyncHealth.STALE_AFTER_MS,
            lastOkAt = now - SyncHealth.STALE_AFTER_MS,
            lastAttemptOk = true,
        )
        assertEquals(SyncHealth.Verdict.STALE, SyncHealth.verdict(state, now))
    }

    @Test
    fun `свежая установка молчит — жаловаться не на что`() {
        assertEquals(SyncHealth.Verdict.FINE, SyncHealth.verdict(SyncHealth.State.UNKNOWN, now))
    }

    @Test
    fun `неудача без единой попытки тревоги не поднимает`() {
        // lastAttemptOk=false при пустом lastAttemptAt — состояние с диска, которого
        // не было; считаем, что попыток не было.
        val state = SyncHealth.State(lastAttemptAt = null, lastOkAt = null, lastAttemptOk = false)
        assertEquals(SyncHealth.Verdict.FINE, SyncHealth.verdict(state, now))
    }

    @Test
    fun `часы с последнего успеха считаются вниз`() {
        val state = SyncHealth.State(lastAttemptAt = now, lastOkAt = now - hour * 30 - 1, lastAttemptOk = true)
        assertEquals(30L, SyncHealth.hoursSinceOk(state, now))
    }

    @Test
    fun `часов нет, если не уезжало ни разу`() {
        assertNull(SyncHealth.hoursSinceOk(SyncHealth.State.UNKNOWN, now))
    }

    @Test
    fun `часы не уходят в минус при часах, переведённых назад`() {
        val state = SyncHealth.State(lastAttemptAt = now, lastOkAt = now + hour, lastAttemptOk = true)
        assertEquals(0L, SyncHealth.hoursSinceOk(state, now))
    }
}
