package com.sasha.alarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class NightAudioTest {

    private val zone = ZoneId.of("Europe/Moscow")

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun deadlineOf(text: String): String =
        java.time.Instant.ofEpochMilli(NightAudio.deadline(at(text), zone))
            .atZone(zone)
            .toLocalDateTime()
            .toString()

    // ─────────────────────────── крайний срок ───────────────────────────

    @Test
    fun `запись с вечера встаёт утром того же сна`() {
        assertEquals("2026-08-27T09:00", deadlineOf("2026-08-26T23:10"))
    }

    @Test
    fun `запись после полуночи встаёт тем же утром`() {
        // Лёг в час ночи — до девяти утра восемь часов, а не тридцать два.
        assertEquals("2026-08-27T09:00", deadlineOf("2026-08-27T01:00"))
    }

    @Test
    fun `запись ранним утром встаёт через час`() {
        assertEquals("2026-08-27T09:00", deadlineOf("2026-08-27T08:00"))
    }

    @Test
    fun `запись после девяти утра ждёт следующего утра`() {
        // Нажал днём — предел не в прошлом, иначе запись оборвалась бы сразу.
        assertEquals("2026-08-28T09:00", deadlineOf("2026-08-27T10:00"))
    }

    @Test
    fun `ровно девять утра считается прошедшим сроком`() {
        // Иначе запись, начатая ровно в 9:00, закончилась бы в ту же миллисекунду.
        assertEquals("2026-08-28T09:00", deadlineOf("2026-08-27T09:00"))
    }

    @Test
    fun `срок всегда в будущем`() {
        for (hour in 0..23) {
            val start = at("2026-08-27T%02d:30".format(hour))
            assertTrue("час $hour", NightAudio.deadline(start, zone) > start)
        }
    }

    @Test
    fun `срок не длиннее суток`() {
        for (hour in 0..23) {
            val start = at("2026-08-27T%02d:30".format(hour))
            val hours = (NightAudio.deadline(start, zone) - start) / 3_600_000.0
            assertTrue("час $hour дал $hours", hours <= 24.0)
        }
    }

    // ─────────────────────────── имя файла ───────────────────────────

    @Test
    fun `ночь названа по утру, а не по вечеру`() {
        assertEquals("noch-2026-08-27-2310.wav", NightAudio.fileName(at("2026-08-26T23:10"), zone))
    }

    @Test
    fun `запись после полуночи попадает в ту же ночь`() {
        // Вечерняя и послеполуночная записи — одна ночь, значит и дата в имени одна.
        assertEquals("noch-2026-08-27-0105.wav", NightAudio.fileName(at("2026-08-27T01:05"), zone))
    }

    @Test
    fun `две записи за ночь не затирают друг друга`() {
        val first = NightAudio.fileName(at("2026-08-26T23:10"), zone)
        val second = NightAudio.fileName(at("2026-08-27T00:40"), zone)
        assertTrue("$first vs $second", first != second)
    }

    // ─────────────────────────── заголовок WAV ───────────────────────────

    @Test
    fun `заголовок ровно сорок четыре байта`() {
        assertEquals(44, NightAudio.header(0).size)
    }

    @Test
    fun `заголовок начинается с RIFF и WAVE`() {
        val h = NightAudio.header(1000)
        assertEquals("RIFF", String(h, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(h, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(h, 12, 4, Charsets.US_ASCII))
        assertEquals("data", String(h, 36, 4, Charsets.US_ASCII))
    }

    @Test
    fun `в заголовке записаны оба размера`() {
        val data = 32_000
        val h = NightAudio.header(data)
        assertEquals(36 + data, int32(h, 4))
        assertEquals(data, int32(h, 40))
    }

    @Test
    fun `формат — несжатый моно шестнадцать бит`() {
        val h = NightAudio.header(0)
        assertEquals(16, int32(h, 16))          // длина блока fmt
        assertEquals(1, int16(h, 20))           // PCM
        assertEquals(1, int16(h, 22))           // каналов
        assertEquals(16_000, int32(h, 24))      // частота
        assertEquals(32_000, int32(h, 28))      // байт в секунду
        assertEquals(2, int16(h, 32))           // размер кадра
        assertEquals(16, int16(h, 34))          // бит на отсчёт
    }

    @Test
    fun `байт в секунду сходится с частотой и разрядностью`() {
        assertEquals(
            NightAudio.SAMPLE_RATE * NightAudio.CHANNELS * NightAudio.BITS_PER_SAMPLE / 8,
            NightAudio.BYTES_PER_SECOND,
        )
    }

    @Test
    fun `восьмичасовая ночь весит около девятисот мегабайт`() {
        // Число из устава: если оно поедет, о нём должны узнать здесь, а не на полном диске.
        val bytes = NightAudio.BYTES_PER_SECOND.toLong() * 8 * 3600
        assertEquals(921.6, bytes / 1_000_000.0, 0.1)
    }

    private fun int32(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or
            (bytes[at + 1].toInt() and 0xFF shl 8) or
            (bytes[at + 2].toInt() and 0xFF shl 16) or
            (bytes[at + 3].toInt() and 0xFF shl 24)

    private fun int16(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or (bytes[at + 1].toInt() and 0xFF shl 8)
}
