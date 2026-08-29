package com.sasha.alarm.core

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Разбор экспорта Sleep Cycle.
 *
 * Единственный источник данных о самом сне: во сколько лёг, сколько спал, сколько
 * засыпал. Приложение отдаёт их только файлом и только руками, поэтому импорт ручной.
 *
 * ⚠️ **Половина работы здесь — отсеять мусор.** В экспорте вперемешку с настоящими ночами
 * лежат обрывки: владелец запустил отслеживание, передумал, выключил. У таких строк
 * `Time asleep = 0`, длительность 15–70 минут и качество 3–15 %. Если их не выбросить,
 * они утянут вниз любое среднее — а именно средние и есть весь смысл этого файла.
 *
 * Прочие особенности формата, все встречены в реальном файле: BOM в начале, разделитель
 * `;`, проценты с хвостом `%`, пустые поля, тире вместо числа в регулярности, строки
 * короче заголовка.
 */
object SleepCycleCsv {

    private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private const val COL_TO_BED = "went to bed"
    private const val COL_WOKE = "woke up"
    private const val COL_IN_BED = "time in bed (seconds)"
    private const val COL_ASLEEP = "time asleep (seconds)"
    private const val COL_LATENCY = "asleep after (seconds)"
    private const val COL_QUALITY = "sleep quality"
    private const val COL_REGULARITY = "regularity"
    private const val COL_SNORE = "snore time (seconds)"
    private const val COL_NOISE = "ambient noise (db)"
    private const val COL_LIGHT = "ambient light (lux)"
    private const val COL_MOVEMENTS = "movements per hour"
    private const val COL_STEPS = "steps"
    private const val COL_ALERT_SCORE = "alertness score"
    private const val COL_ALERT_TIME = "alertness reaction time (seconds)"
    private const val COL_ALERT_ACC = "alertness accuracy"

    /**
     * Итог разбора файла.
     *
     * Отброшенное считается и показывается: «импортировано 118 ночей, пропущено 14 пустых»
     * честнее, чем молча показать 118 и оставить владельца гадать, куда делось остальное.
     */
    data class Result(
        val sessions: List<LogEvent>,
        val emptySessions: Int,
        val badRows: Int,
    )

    /**
     * @param text содержимое файла
     * @param zone часовой пояс, в котором записаны времена. В файле его нет вовсе —
     *             Sleep Cycle пишет местное время без смещения, так что подставляет вызывающий.
     */
    fun parse(text: String, zone: ZoneId): Result {
        val lines = text.removePrefix("﻿").split(Regex("\r?\n"))
        val header = lines.firstOrNull { it.isNotBlank() } ?: return Result(emptyList(), 0, 0)
        val columns = header.split(';').withIndex().associate { (i, name) -> name.trim().lowercase() to i }

        if (COL_TO_BED !in columns || COL_WOKE !in columns || COL_ASLEEP !in columns) {
            return Result(emptyList(), 0, 0)
        }

        val sessions = ArrayList<LogEvent>()
        var empty = 0
        var bad = 0

        for (line in lines.drop(lines.indexOf(header) + 1)) {
            if (line.isBlank()) continue
            val cells = line.split(';')

            fun cell(name: String): String? =
                columns[name]?.let { cells.getOrNull(it) }?.trim()?.takeIf { it.isNotEmpty() }

            val toBed = cell(COL_TO_BED)?.let { moment(it, zone) }
            val woke = cell(COL_WOKE)?.let { moment(it, zone) }
            if (toBed == null || woke == null || woke < toBed) {
                bad++
                continue
            }

            val asleep = cell(COL_ASLEEP)?.toLongOrNull()
            if (asleep == null) {
                bad++
                continue
            }
            if (asleep <= 0L) {
                // Запустил отслеживание и передумал. Это не ночь.
                empty++
                continue
            }

            val data = LinkedHashMap<String, LogValue>()
            data["toBedMillis"] = LogValue.of(toBed)
            data["wokeMillis"] = LogValue.of(woke)
            data["asleepSec"] = LogValue.of(asleep)
            cell(COL_IN_BED)?.toLongOrNull()?.let { data["inBedSec"] = LogValue.of(it) }
            cell(COL_LATENCY)?.toLongOrNull()?.let { data["latencySec"] = LogValue.of(it) }
            percent(cell(COL_QUALITY))?.let { data["quality"] = LogValue.of(it) }
            percent(cell(COL_REGULARITY))?.let { data["regularity"] = LogValue.of(it) }
            cell(COL_SNORE)?.toLongOrNull()?.let { data["snoreSec"] = LogValue.of(it) }
            cell(COL_STEPS)?.toLongOrNull()?.takeIf { it > 0 }?.let { data["steps"] = LogValue.of(it) }
            decimal(cell(COL_NOISE))?.let { data["noiseDb"] = LogValue.of(it) }
            decimal(cell(COL_LIGHT))?.let { data["lux"] = LogValue.of(it) }
            decimal(cell(COL_MOVEMENTS))?.let { data["movementsPerHour"] = LogValue.of(it) }
            decimal(cell(COL_ALERT_SCORE))?.let { data["alertnessScore"] = LogValue.of(it) }
            decimal(cell(COL_ALERT_TIME))?.let { data["alertnessSec"] = LogValue.of(it) }
            decimal(cell(COL_ALERT_ACC))?.let { data["alertnessAccuracy"] = LogValue.of(it) }

            sessions.add(
                LogEvent(
                    // Ночь привязывается к пробуждению, а не к отбою: отчётные сутки
                    // называются по утру, и якорем должно быть именно оно (см. DayLog).
                    at = woke,
                    tzOffsetMinutes = zone.rules.getOffset(java.time.Instant.ofEpochMilli(woke)).totalSeconds / 60,
                    type = EventType.SLEEP_SESSION,
                    data = data,
                )
            )
        }

        return Result(sessions.sortedBy { it.at }, empty, bad)
    }

    /**
     * Ключ ночи для сверки при повторном импорте: один и тот же файл, залитый дважды,
     * не должен удвоить историю.
     */
    fun sessionKey(event: LogEvent): Pair<Long, Long>? {
        val toBed = event.long("toBedMillis") ?: return null
        val woke = event.long("wokeMillis") ?: return null
        return toBed to woke
    }

    private fun moment(text: String, zone: ZoneId): Long? = try {
        LocalDateTime.parse(text, TIMESTAMP).atZone(zone).toInstant().toEpochMilli()
    } catch (e: java.time.format.DateTimeParseException) {
        null // строка испорчена — считаем её плохой, а не роняем весь импорт
    }

    /** «74%» → 74. Тире, прочерк и пустое — отсутствующее значение. */
    private fun percent(text: String?): Long? =
        text?.removeSuffix("%")?.trim()?.toLongOrNull()

    private fun decimal(text: String?): Double? =
        text?.toDoubleOrNull()?.takeIf { it.isFinite() }
}
