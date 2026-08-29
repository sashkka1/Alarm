package com.sasha.alarm.core

/**
 * Событие ⇄ одна строка файла.
 *
 * Формат — JSON по строке на событие (JSONL). Выбран потому, что дозапись такого файла
 * это дописывание строки в конец: не нужно ни перечитывать, ни переписывать уже лежащее,
 * а оборванная на полуслове последняя строка портит ровно себя. Для журнала, который
 * пишется во время тревоги и может быть прерван убийством процесса, это главное свойство.
 *
 * Разбор написан руками, без библиотеки сериализации: `:core` не имеет ни одной внешней
 * зависимости, и заводить первую ради формата, который мы сами же и пишем, не стоит.
 *
 * Строка выглядит так:
 * ```
 * {"v":1,"at":1756100000000,"tz":180,"t":"nfc.tag","d":{"index":0,"lux":12500.5}}
 * ```
 *
 * ⚠️ Ни одна функция здесь не бросает исключений наружу: журнал пишется на критическом
 * пути «зазвонил → показан экран», и испорченная строка обязана стоить одной строки,
 * а не тревоги (P0 №7).
 */
object LogCodec {

    private const val KEY_SCHEMA = "v"
    private const val KEY_AT = "at"
    private const val KEY_TZ = "tz"
    private const val KEY_TYPE = "t"
    private const val KEY_DATA = "d"

    /** Событие в строку. Переводов строки внутри не будет — они экранируются. */
    fun encode(event: LogEvent): String = buildString {
        append('{')
        append("\"").append(KEY_SCHEMA).append("\":").append(event.schema)
        append(",\"").append(KEY_AT).append("\":").append(event.at)
        append(",\"").append(KEY_TZ).append("\":").append(event.tzOffsetMinutes)
        append(",\"").append(KEY_TYPE).append("\":")
        appendQuoted(event.type)
        if (event.data.isNotEmpty()) {
            append(",\"").append(KEY_DATA).append("\":{")
            var first = true
            for ((key, value) in event.data) {
                if (!first) append(',')
                first = false
                appendQuoted(key)
                append(':')
                appendValue(value)
            }
            append('}')
        }
        append('}')
    }

    /**
     * Строка в событие. `null` — строку прочитать не удалось.
     *
     * Требуются только [LogEvent.at], [LogEvent.tzOffsetMinutes] и [LogEvent.type]:
     * без них строка бессмысленна. Всё остальное необязательно, а поле неизвестной
     * формы (вложенный объект от будущей версии) пропускается — потерять одно поле
     * лучше, чем потерять всё утро.
     */
    fun decode(line: String): LogEvent? {
        if (line.isBlank()) return null
        val root = Json.parse(line) as? Map<*, *> ?: return null
        val at = (root[KEY_AT] as? Long) ?: return null
        val tz = (root[KEY_TZ] as? Long)?.toInt() ?: return null
        val type = (root[KEY_TYPE] as? String)?.takeIf { it.isNotEmpty() } ?: return null
        val schema = (root[KEY_SCHEMA] as? Long)?.toInt() ?: LogEvent.SCHEMA
        val data = LinkedHashMap<String, LogValue>()
        (root[KEY_DATA] as? Map<*, *>)?.forEach { (rawKey, rawValue) ->
            val key = rawKey as? String ?: return@forEach
            toValue(rawValue)?.let { data[key] = it }
        }
        return LogEvent(at = at, tzOffsetMinutes = tz, type = type, data = data, schema = schema)
    }

    /**
     * Разбор всего файла. Нечитаемые строки молча пропускаются — их число возвращать
     * некому, а падать на обрывке последней записи журнал не должен.
     */
    fun decodeAll(lines: Sequence<String>): List<LogEvent> =
        lines.mapNotNull { decode(it) }.toList()

    fun decodeAll(lines: Iterable<String>): List<LogEvent> = decodeAll(lines.asSequence())

    private fun toValue(raw: Any?): LogValue? = when (raw) {
        is String -> LogValue.Text(raw)
        is Long -> LogValue.Integer(raw)
        is Double -> if (raw.isFinite()) LogValue.Decimal(raw) else null
        is Boolean -> LogValue.Flag(raw)
        else -> null // null, вложенный объект или массив — поле пропускаем
    }

    private fun StringBuilder.appendValue(value: LogValue) {
        when (value) {
            is LogValue.Text -> appendQuoted(value.value)
            is LogValue.Integer -> append(value.value)
            is LogValue.Flag -> append(if (value.value) "true" else "false")
            // NaN и бесконечность в JSON не существуют: датчик, отдавший мусор,
            // стоит одного поля, а не всей строки.
            is LogValue.Decimal -> if (value.value.isFinite()) append(value.value) else append("null")
        }
    }

    private fun StringBuilder.appendQuoted(text: String) {
        append('"')
        for (c in text) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c.code < 0x20 -> {
                    append("\\u")
                    append(c.code.toString(16).padStart(4, '0'))
                }
                else -> append(c)
            }
        }
        append('"')
    }

}
