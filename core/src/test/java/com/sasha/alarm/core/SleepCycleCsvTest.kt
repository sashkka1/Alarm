package com.sasha.alarm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SleepCycleCsvTest {

    private val minsk = ZoneId.of("Europe/Minsk") // UTC+3 круглый год

    private val header =
        "Went to bed;Woke up;Sleep Quality;Time in bed (seconds);Time asleep (seconds);" +
            "Asleep after (seconds);Regularity;Did snore;Snore time (seconds);Coughing (per hour);" +
            "Steps;City;Breathing disruptions (per hour);Ambient noise (dB);Ambient light (lux);" +
            "Alertness score;Alertness reaction time (seconds);Alertness accuracy;Movements per hour;" +
            "Wake up window start;Wake up window stop"

    // Строки взяты из настоящего экспорта владельца.
    private val realNight =
        "2026-08-24 23:10:04;2026-08-25 07:53:17;93%;31392;26369;3767;77%;false;0;0.0;0;;0.0;" +
            "24.74127;0.0;;;;3.090732;26-08-25 07:32:38;26-08-25 08:00:00"

    private val junkNight =
        "2026-08-23 22:57:58;2026-08-23 23:43:21;8%;2723;0;2723;58%;false;0;1.0;0;;0.0;" +
            "25.395576;0.0;;;;1.0;26-08-24 07:30:35;26-08-24 08:00:00"

    private val longNight =
        "2026-08-22 22:56:37;2026-08-23 09:53:12;97%;39395;35324;2363;85%;false;0;0.0;0;;0.0;" +
            "25.013481;0.0;;;;2.7196069;;"

    private fun parse(vararg rows: String, prefix: String = "") =
        SleepCycleCsv.parse((prefix + header + "\n" + rows.joinToString("\n")), minsk)

    @Test
    fun `настоящая ночь разбирается со всеми полями`() {
        val night = parse(realNight).sessions.single()

        assertEquals(EventType.SLEEP_SESSION, night.type)
        assertEquals(26369L, night.long("asleepSec"))
        assertEquals(31392L, night.long("inBedSec"))
        assertEquals(3767L, night.long("latencySec"))
        assertEquals(93L, night.long("quality"))
        assertEquals(77L, night.long("regularity"))
        assertEquals(24.74127, night.double("noiseDb") ?: 0.0, 0.00001)
        assertEquals(3.090732, night.double("movementsPerHour") ?: 0.0, 0.00001)
        assertEquals(180, night.tzOffsetMinutes)
    }

    @Test
    fun `ночь привязана к пробуждению а не к отбою`() {
        // Отчётные сутки называются по утру, поэтому якорь — пробуждение.
        val night = parse(realNight).sessions.single()
        assertEquals(night.long("wokeMillis"), night.at)
        assertTrue((night.long("toBedMillis") ?: 0L) < night.at)
    }

    // ⚠️ Проверка «ночь попадает в отчётные сутки своего утра» уехала в Sashboard вместе
    // с самим правилом `DayLog.reportDate` (ADR-0011): отчётные сутки считает компьютер,
    // телефону они не нужны. Здесь остался разбор CSV — он нужен обоим.

    @Test
    fun `обрывок отслеживания не считается ночью`() {
        // Запустил трекинг, передумал, выключил: Time asleep = 0. Такие строки утянули бы
        // вниз любое среднее — ради этого разбор вообще и написан.
        val result = parse(junkNight)

        assertTrue(result.sessions.isEmpty())
        assertEquals(1, result.emptySessions)
        assertEquals(0, result.badRows)
    }

    @Test
    fun `настоящие ночи отделяются от обрывков в одном файле`() {
        val result = parse(longNight, junkNight, realNight)

        assertEquals(2, result.sessions.size)
        assertEquals(1, result.emptySessions)
    }

    @Test
    fun `ночи идут по возрастанию времени`() {
        val result = parse(realNight, longNight)
        assertEquals(listOf(35324L, 26369L), result.sessions.map { it.long("asleepSec") })
    }

    @Test
    fun `BOM в начале файла не мешает`() {
        val result = parse(realNight, prefix = "﻿")
        assertEquals(1, result.sessions.size)
    }

    @Test
    fun `испорченная дата делает строку плохой но не роняет импорт`() {
        val broken = realNight.replaceFirst("2026-08-24 23:10:04", "не дата")
        val result = parse(broken, realNight)

        assertEquals(1, result.sessions.size)
        assertEquals(1, result.badRows)
    }

    @Test
    fun `пробуждение раньше отбоя это плохая строка`() {
        val broken = realNight.replaceFirst("2026-08-25 07:53:17", "2026-08-24 20:00:00")
        assertEquals(1, parse(broken).badRows)
    }

    @Test
    fun `тире вместо регулярности не ломает строку`() {
        // В файле владельца такое встречается в первых записях.
        val dashed = realNight.replaceFirst(";77%;", ";—;")
        val night = parse(dashed).sessions.single()

        assertNull(night.long("regularity"))
        assertEquals(26369L, night.long("asleepSec"))
    }

    @Test
    fun `пустые поля просто отсутствуют`() {
        val night = parse(realNight).sessions.single()
        // Тест бодрости владелец пока не проходит — колонки пустые.
        assertNull(night.double("alertnessScore"))
        assertNull(night.double("alertnessSec"))
    }

    @Test
    fun `нулевые шаги не пишутся`() {
        val night = parse(realNight).sessions.single()
        assertNull(night.long("steps"))
    }

    @Test
    fun `шаги пишутся когда они есть`() {
        val walked = realNight.replaceFirst(";0.0;0;;0.0;", ";0.0;4200;;0.0;")
        assertEquals(4200L, parse(walked).sessions.single().long("steps"))
    }

    @Test
    fun `строка короче заголовка не считается плохой`() {
        // Sleep Cycle иногда обрезает хвост колонок.
        val short = "2026-08-24 23:10:04;2026-08-25 07:53:17;93%;31392;26369;3767"
        val night = parse(short).sessions.single()

        assertEquals(26369L, night.long("asleepSec"))
        assertNull(night.double("noiseDb"))
    }

    @Test
    fun `файл без нужных колонок не разбирается`() {
        val result = SleepCycleCsv.parse("что-то;совсем;другое\n1;2;3", minsk)
        assertTrue(result.sessions.isEmpty())
    }

    @Test
    fun `пустой файл не роняет разбор`() {
        assertTrue(SleepCycleCsv.parse("", minsk).sessions.isEmpty())
        assertTrue(SleepCycleCsv.parse("\n\n", minsk).sessions.isEmpty())
    }

    @Test
    fun `ключ ночи одинаков при повторном импорте того же файла`() {
        val first = parse(realNight).sessions.single()
        val second = parse(realNight).sessions.single()

        assertNotNull(SleepCycleCsv.sessionKey(first))
        assertEquals(SleepCycleCsv.sessionKey(first), SleepCycleCsv.sessionKey(second))
    }

    @Test
    fun `ночь переживает запись в журнал и чтение обратно`() {
        val night = parse(realNight).sessions.single()
        assertEquals(night, LogCodec.decode(LogCodec.encode(night)))
    }

    @Test
    fun `переводы строк windows не мешают`() {
        val text = header + "\r\n" + realNight + "\r\n"
        assertEquals(1, SleepCycleCsv.parse(text, minsk).sessions.size)
    }
}
