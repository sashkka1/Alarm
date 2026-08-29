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
    fun `приветствие чужой версии не принимается`() {
        // Сервер старше клиента или наоборот — лучше отказать, чем разобрать наполовину.
        assertFalse(LogWire.isHello("ALARMLOG 2"))
        assertFalse(LogWire.isHello("ALARMLOG x"))
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
