package com.sasha.alarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogCodecTest {

    private val sample = LogEvent(
        at = 1_756_100_000_000L,
        tzOffsetMinutes = 180,
        type = EventType.NFC_TAG,
        data = mapOf(
            "index" to LogValue.of(0L),
            "lux" to LogValue.of(12_500.5),
            "outdoor" to LogValue.of(true),
            "note" to LogValue.of("уличная метка"),
        ),
    )

    @Test
    fun `событие переживает запись и чтение целиком`() {
        assertEquals(sample, LogCodec.decode(LogCodec.encode(sample)))
    }

    @Test
    fun `миллисекунды эпохи не теряют точности`() {
        // Ради этого целые и дробные разведены по разным типам: в Double такое число уже неточно.
        val decoded = LogCodec.decode(LogCodec.encode(sample))
        assertEquals(1_756_100_000_000L, decoded?.at)
    }

    @Test
    fun `в строке никогда нет перевода строки`() {
        val event = sample.copy(data = mapOf("note" to LogValue.of("две\nстроки\tи\rвозврат")))
        val line = LogCodec.encode(event)
        assertFalse(line.contains('\n'))
        assertFalse(line.contains('\r'))
        assertEquals(event, LogCodec.decode(line))
    }

    @Test
    fun `кавычки и слеши экранируются`() {
        val event = sample.copy(data = mapOf("note" to LogValue.of("""он сказал "да" и ушёл в C:\путь""")))
        assertEquals(event, LogCodec.decode(LogCodec.encode(event)))
    }

    @Test
    fun `управляющие символы не ломают строку`() {
        val event = sample.copy(data = mapOf("note" to LogValue.of("до\u0001после")))
        assertEquals(event, LogCodec.decode(LogCodec.encode(event)))
    }

    @Test
    fun `событие без полей пишется без блока данных`() {
        val event = LogEvent(at = 1_000L, tzOffsetMinutes = 0, type = EventType.PHONE_BOOT)
        val line = LogCodec.encode(event)
        assertFalse(line.contains("\"d\""))
        assertEquals(event, LogCodec.decode(line))
    }

    @Test
    fun `отрицательное смещение часового пояса переживает запись`() {
        val event = sample.copy(tzOffsetMinutes = -330)
        assertEquals(-330, LogCodec.decode(LogCodec.encode(event))?.tzOffsetMinutes)
    }

    @Test
    fun `мусорная строка не читается и не роняет разбор`() {
        assertNull(LogCodec.decode("это не json"))
        assertNull(LogCodec.decode("{"))
        assertNull(LogCodec.decode("{\"at\":}"))
        assertNull(LogCodec.decode(""))
        assertNull(LogCodec.decode("   "))
    }

    @Test
    fun `строка без обязательных полей не читается`() {
        assertNull(LogCodec.decode("""{"v":1,"tz":180,"t":"x"}"""))      // нет at
        assertNull(LogCodec.decode("""{"v":1,"at":1,"t":"x"}"""))        // нет tz
        assertNull(LogCodec.decode("""{"v":1,"at":1,"tz":180}"""))       // нет типа
        assertNull(LogCodec.decode("""{"v":1,"at":1,"tz":180,"t":""}""")) // пустой тип
    }

    @Test
    fun `оборванная на полуслове строка стоит только себя`() {
        // Ровно то, что остаётся в файле, если процесс убили посреди записи.
        val whole = LogCodec.encode(sample)
        val cut = whole.substring(0, whole.length / 2)
        assertNull(LogCodec.decode(cut))

        val file = listOf(whole, whole, cut)
        assertEquals(2, LogCodec.decodeAll(file).size)
    }

    @Test
    fun `пустые строки в файле пропускаются`() {
        val file = listOf("", LogCodec.encode(sample), "   ", "")
        assertEquals(1, LogCodec.decodeAll(file).size)
    }

    @Test
    fun `неизвестный тип события доезжает целым`() {
        // Читатель может быть старше писателя — это не повод терять строку.
        val event = sample.copy(type = "что.то.из.будущего")
        assertEquals(event, LogCodec.decode(LogCodec.encode(event)))
    }

    @Test
    fun `поле неизвестной формы пропускается а событие остаётся`() {
        val line = """{"v":2,"at":5,"tz":60,"t":"x","d":{"ok":7,"nested":{"a":1},"list":[1,2]}}"""
        val decoded = LogCodec.decode(line)
        assertNotNull(decoded)
        assertEquals(7L, decoded?.long("ok"))
        assertEquals(1, decoded?.data?.size)
        assertEquals(2, decoded?.schema)
    }

    @Test
    fun `версия схемы по умолчанию проставляется если её нет в строке`() {
        val decoded = LogCodec.decode("""{"at":5,"tz":60,"t":"x"}""")
        assertEquals(LogEvent.SCHEMA, decoded?.schema)
    }

    @Test
    fun `мусор от датчика стоит одного поля а не строки`() {
        // NaN в JSON не существует. Падать из-за этого во время тревоги нельзя (P0 №7).
        val event = sample.copy(data = mapOf("lux" to LogValue.of(Double.NaN), "index" to LogValue.of(0L)))
        val decoded = LogCodec.decode(LogCodec.encode(event))
        assertNotNull(decoded)
        assertNull(decoded?.double("lux"))
        assertEquals(0L, decoded?.long("index"))
    }

    @Test
    fun `бесконечность тоже не ломает строку`() {
        val event = sample.copy(data = mapOf("lux" to LogValue.of(Double.POSITIVE_INFINITY)))
        assertNotNull(LogCodec.decode(LogCodec.encode(event)))
    }

    @Test
    fun `целое читается как целое а дробное как дробное`() {
        val decoded = LogCodec.decode("""{"at":5,"tz":0,"t":"x","d":{"i":42,"d":42.0,"e":1.5e3}}""")
        assertTrue(decoded?.data?.get("i") is LogValue.Integer)
        assertTrue(decoded?.data?.get("d") is LogValue.Decimal)
        assertEquals(1500.0, decoded?.double("e") ?: 0.0, 0.0001)
    }

    @Test
    fun `чтение поля чужого типа возвращает null а не падает`() {
        val decoded = LogCodec.decode(LogCodec.encode(sample))
        assertNull(decoded?.text("index"))
        assertNull(decoded?.flag("lux"))
        assertNull(decoded?.long("нет такого поля"))
    }

    @Test
    fun `лишний хвост после json не принимается`() {
        assertNull(LogCodec.decode("""{"at":5,"tz":0,"t":"x"} мусор"""))
    }
}
