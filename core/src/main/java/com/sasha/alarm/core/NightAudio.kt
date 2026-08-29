package com.sasha.alarm.core

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Правила ночной записи звука.
 *
 * Записывается **вся ночь целиком**, чтобы потом строить по ней свой разбор сна и сверять
 * его со Sleep Cycle (решение владельца 2026-08-26). Поэтому здесь нет ни сжатия, ни
 * порогов тишины, ни вырезания пауз: всё, что выброшено при записи, обратно не возвращается,
 * а какой именно признак понадобится разбору — сейчас неизвестно.
 *
 * ⚠️ **Ночь весит около гигабайта** (`16000 × 2 байта × 8 часов ≈ 900 МБ`). Это осознанная
 * плата за несжатый звук: тихое дыхание и шорох простыни кодек с потерями съедает первым,
 * а именно они и есть предмет будущего разбора.
 */
object NightAudio {

    /**
     * 16 кГц — верхняя граница слышимого в записи около 8 кГц.
     *
     * Хватает и на дыхание, и на шорохи, и на голос; выше начинается то, чего в спящей
     * комнате всё равно нет, а вес растёт линейно.
     */
    const val SAMPLE_RATE = 16_000

    const val BITS_PER_SAMPLE = 16
    const val CHANNELS = 1

    /** Сколько байт занимает секунда записи. */
    const val BYTES_PER_SECOND = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8

    /** Размер заголовка WAV. Он же — смещение, с которого начинается сам звук. */
    const val HEADER_BYTES = 44

    /**
     * Крайний срок записи — **ближайшие 9:00 после её начала** (решение владельца 2026-08-26).
     *
     * Обычно запись останавливает звонок будильника, но он может и не прозвучать: отслеживание
     * сна не запустилось, будильник выключен, запись нажата днём. Без предела такая запись
     * слушала бы комнату вторые сутки и съела бы память телефона.
     *
     * ⚠️ Срок именно календарный, а не «через N часов»: 9:00 — это про утро, а не про
     * длительность. Нажатая в 23:00 запись встанет в 9:00 через десять часов, нажатая в
     * 8:00 — через час, нажатая в 10:00 — следующим утром.
     */
    fun deadline(startMillis: Long, zone: ZoneId): Long {
        val start = Instant.ofEpochMilli(startMillis).atZone(zone)
        val todayNine = start.toLocalDate().atTime(LIMIT_HOUR).atZone(zone)
        val nine = if (todayNine.toInstant().toEpochMilli() > startMillis) {
            todayNine
        } else {
            todayNine.plusDays(1)
        }
        return nine.toInstant().toEpochMilli()
    }

    /** Час, после которого запись не продолжается. */
    val LIMIT_HOUR: LocalTime = LocalTime.of(9, 0)

    /**
     * Имя файла записи — по **утру**, а не по вечеру.
     *
     * Ночь с 26-го на 27-е называется `noch-2026-08-27.wav`. Так же названы отчётные сутки
     * в [DayLog] и так же выгружает ночи Sleep Cycle — иначе сверять пришлось бы, каждый
     * раз вспоминая, какой из двух дней имелся в виду.
     *
     * Время начала в имени есть намеренно: за одну ночь записей может быть несколько
     * (остановил, начал заново), и перезаписывать первую нельзя.
     */
    fun fileName(startMillis: Long, zone: ZoneId): String {
        val morning = Instant.ofEpochMilli(deadline(startMillis, zone)).atZone(zone).toLocalDate()
        val started = Instant.ofEpochMilli(startMillis).atZone(zone)
        return "noch-${morning.format(DAY)}-${started.format(TIME)}.wav"
    }

    private val DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val TIME = DateTimeFormatter.ofPattern("HHmm")

    /**
     * Заголовок WAV на 44 байта.
     *
     * ⚠️ В нём **дважды** записан размер, и оба поля становятся известны только когда запись
     * закончилась. Поэтому заголовок пишется в файл дважды: пустой заглушкой в начале, чтобы
     * занять место, и настоящий — поверх неё, когда длина известна. Файл, оборванный на
     * середине (телефон выключился, процесс убит), останется с нулями в этих полях — звук в
     * нём цел, но проигрыватель сочтёт его пустым. Чинится тем же вызовом с реальной длиной.
     */
    fun header(dataBytes: Int): ByteArray {
        val out = ByteArray(HEADER_BYTES)
        var at = 0

        fun ascii(text: String) {
            for (ch in text) out[at++] = ch.code.toByte()
        }

        fun int32(value: Int) {
            out[at++] = (value and 0xFF).toByte()
            out[at++] = (value shr 8 and 0xFF).toByte()
            out[at++] = (value shr 16 and 0xFF).toByte()
            out[at++] = (value shr 24 and 0xFF).toByte()
        }

        fun int16(value: Int) {
            out[at++] = (value and 0xFF).toByte()
            out[at++] = (value shr 8 and 0xFF).toByte()
        }

        ascii("RIFF")
        int32(HEADER_BYTES - 8 + dataBytes)
        ascii("WAVE")

        ascii("fmt ")
        int32(16)
        int16(1) // PCM без сжатия
        int16(CHANNELS)
        int32(SAMPLE_RATE)
        int32(BYTES_PER_SECOND)
        int16(CHANNELS * BITS_PER_SAMPLE / 8)
        int16(BITS_PER_SAMPLE)

        ascii("data")
        int32(dataBytes)

        return out
    }
}
