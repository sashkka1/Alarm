package com.sasha.alarm.core

import com.sasha.alarm.core.HookHealth.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HookHealthTest {

    private val now = 1_000_000_000L

    private fun judge(downSince: Long?, suspectSince: Long?, status: Status, rebound: Boolean = false) =
        HookHealth.judge(downSince, suspectSince, status, rebound, now)

    @Test
    fun `исправный зацеп — тишина`() {
        val outcome = judge(null, null, Status.WORKING)
        assertNull(outcome.downSince)
        assertNull(outcome.suspectSince)
        assertNull(outcome.entry)
        assertFalse(outcome.showNotice)
        assertFalse(outcome.sendJournalNow)
        assertFalse(outcome.recheckSoon)
    }

    @Test
    fun `подняли сразу — в журнал без уведомления`() {
        val outcome = judge(null, null, Status.WORKING, rebound = true)
        assertEquals(EventType.HOOK_REBOUND, outcome.entry?.type)
        assertFalse(outcome.showNotice)
        assertFalse(outcome.sendJournalNow)
    }

    @Test
    fun `впервые не подключён — молча ждём повторной проверки`() {
        val outcome = judge(null, null, Status.UNBOUND)
        assertEquals(now, outcome.suspectSince)
        assertNull(outcome.downSince)
        assertNull(outcome.entry)
        assertFalse(outcome.showNotice)
        assertFalse(outcome.sendJournalNow)
        assertTrue(outcome.recheckSoon)
    }

    @Test
    fun `поднялся после толчка — поломки не было, только строка в журнал`() {
        val outcome = judge(null, suspectSince = now - 40_000L, status = Status.WORKING)
        assertEquals(EventType.HOOK_REBOUND, outcome.entry?.type)
        assertNull(outcome.suspectSince)
        assertFalse(outcome.showNotice)
        assertFalse(outcome.sendJournalNow)
    }

    @Test
    fun `повторная проверка, а он лежит — отрезок с начала подозрения, уведомление, журнал сразу`() {
        val suspect = now - HookHealth.RECHECK_MS
        val outcome = judge(null, suspect, Status.UNBOUND)
        assertEquals(suspect, outcome.downSince)
        assertNull(outcome.suspectSince)
        assertEquals(EventType.HOOK_DOWN, outcome.entry?.type)
        assertEquals(LogValue.of("unbound"), outcome.entry?.data?.get("reason"))
        assertTrue(outcome.showNotice)
        assertTrue(outcome.sendJournalNow)
        assertFalse(outcome.recheckSoon)
    }

    @Test
    fun `отобранный доступ — отрезок сразу, ждать нечего`() {
        val outcome = judge(null, null, Status.NO_ACCESS)
        assertEquals(now, outcome.downSince)
        assertEquals(LogValue.of("no_access"), outcome.entry?.data?.get("reason"))
        assertTrue(outcome.showNotice)
        assertTrue(outcome.sendJournalNow)
    }

    @Test
    fun `отрезок пишется один раз, уведомление держится`() {
        val since = now - 30 * 60_000L
        val outcome = judge(since, null, Status.UNBOUND)
        assertEquals(since, outcome.downSince)
        assertNull(outcome.entry)
        assertTrue(outcome.showNotice)
        assertFalse(outcome.sendJournalNow)
        assertFalse(outcome.recheckSoon)
    }

    @Test
    fun `заработал — отрезок закрывается с длительностью`() {
        val since = now - 45 * 60_000L
        val outcome = judge(since, null, Status.WORKING, rebound = true)
        assertNull(outcome.downSince)
        assertEquals(EventType.HOOK_UP, outcome.entry?.type)
        assertEquals(LogValue.of(45 * 60_000L), outcome.entry?.data?.get("downMs"))
        assertFalse(outcome.showNotice)
    }

    @Test
    fun `часы назад не дают отрицательной длительности`() {
        val outcome = judge(now + 60_000L, null, Status.WORKING)
        assertEquals(LogValue.of(0L), outcome.entry?.data?.get("downMs"))
    }
}
