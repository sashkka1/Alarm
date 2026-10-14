package com.sasha.alarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogWireTest {

    @Test
    fun `приветствие своей версии узнаётся`() {
        assertTrue(LogWire.isHello(LogWire.HELLO))
        assertTrue(LogWire.isHello(" ${LogWire.HELLO} "))
    }

    @Test
    fun `чужое приветствие не принимается`() {
        assertFalse(LogWire.isHello("GET / HTTP/1.1"))
        assertFalse(LogWire.isHello("ALARMLOG"))
        assertFalse(LogWire.isHello(""))
        assertFalse(LogWire.isHello(null))
    }

    @Test
    fun `приветствие незнакомой версии не принимается`() {
        // Разобрать наполовину хуже, чем отказать.
        assertFalse(LogWire.isHello("ALARMLOG ${LogWire.VERSION + 1}"))
        assertFalse(LogWire.isHello("ALARMLOG 0"))
        assertFalse(LogWire.isHello("ALARMLOG x"))
    }

    @Test
    fun `прежняя версия понимается — телефон и компьютер обновляются порознь`() {
        assertTrue(LogWire.isHello("ALARMLOG 1"))
        assertEquals(1, LogWire.helloVersion("ALARMLOG 1"))
        assertEquals(LogWire.VERSION, LogWire.helloVersion(LogWire.HELLO))
    }

    @Test
    fun `версия чужого приветствия не читается`() {
        assertNull(LogWire.helloVersion("GET / HTTP/1.1"))
        assertNull(LogWire.helloVersion("ALARMLOG"))
        assertNull(LogWire.helloVersion(null))
    }

    @Test
    fun `запас компьютера читается обратно`() {
        assertEquals(0, LogWire.parseHave(LogWire.have(0)))
        assertEquals(192_771, LogWire.parseHave(LogWire.have(192_771)))
        assertEquals(7, LogWire.parseHave(" ${LogWire.have(7)} "))
    }

    @Test
    fun `не тот ответ за запас не выдаётся`() {
        // Старый компьютер про запас молчит; принять за него что попало значит недослать.
        assertNull(LogWire.parseHave(null))
        assertNull(LogWire.parseHave("OK 5"))
        assertNull(LogWire.parseHave(LogWire.HAVE))
        assertNull(LogWire.parseHave("${LogWire.HAVE} -1"))
    }

    @Test
    fun `запас и конец пачки не путаются между собой`() {
        assertFalse(LogWire.have(3) == LogWire.END_OF_BATCH)
        assertNull(LogWire.parseHave(LogWire.END_OF_BATCH))
    }

    @Test
    fun `ответ о принятых строках читается обратно`() {
        assertEquals(42, LogWire.parseAccepted(LogWire.accepted(42)))
        assertEquals(0, LogWire.parseAccepted(LogWire.accepted(0)))
    }

    @Test
    fun `непонятный ответ не выдаётся за успех`() {
        assertNull(LogWire.parseAccepted("ERROR"))
        assertNull(LogWire.parseAccepted("OK"))
        assertNull(LogWire.parseAccepted("OK много"))
        assertNull(LogWire.parseAccepted(null))
        assertNull(LogWire.parseAccepted(""))
    }

    @Test
    fun `ответ на поиск читается обратно`() {
        assertEquals(LogWire.TCP_PORT, LogWire.parseDiscoverAnswer(LogWire.discoverAnswer(LogWire.TCP_PORT)))
    }

    @Test
    fun `чужой ответ на поиск отбрасывается`() {
        assertNull(LogWire.parseDiscoverAnswer("SOMETHING-ELSE 45573"))
        assertNull(LogWire.parseDiscoverAnswer("ALARMLOG-HERE 0"))
        assertNull(LogWire.parseDiscoverAnswer("ALARMLOG-HERE 99999"))
        assertNull(LogWire.parseDiscoverAnswer("ALARMLOG-HERE"))
        assertNull(LogWire.parseDiscoverAnswer(null))
    }

    @Test
    fun `конец пачки не может совпасть со строкой журнала`() {
        // Пустая строка выбрана концом пачки именно потому, что событие всегда объект JSON.
        val line = LogCodec.encode(LogEvent(at = 1, tzOffsetMinutes = 0, type = "x"))
        assertTrue(line.isNotEmpty())
        assertEquals("", LogWire.END_OF_BATCH)
    }
}
