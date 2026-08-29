package com.sasha.alarm.core

/**
 * Минимальный разбор JSON.
 *
 * Живёт отдельно, потому что нужен в двух местах: журналу ([LogCodec]) и общению с
 * локальной языковой моделью на компьютере. Писать второй парсер ради второго места —
 * верный способ получить два разных набора ошибок.
 *
 * Подмножество полное для чтения чужого JSON: объекты, массивы, строки с экранированием,
 * числа, `true`/`false`/`null`. Целые отдаются как [Long], дробные как [Double] —
 * миллисекунды эпохи в `Double` уже неточны.
 *
 * ⚠️ Не бросает исключений наружу: [parse] возвращает `null`, если строку прочитать
 * не удалось. Разбор идёт на критическом пути записи журнала, и испорченный ввод
 * обязан стоить строки, а не тревоги (P0 №7).
 */
object Json {

    /** Разобрать текст целиком. `null` — не JSON либо мусор в конце. */
    fun parse(text: String): Any? = try {
        Reader(text).parseWhole()
    } catch (e: Malformed) {
        null
    }

    /** Объект по имени поля, если он там есть. Удобно для вложенных ответов. */
    @Suppress("UNCHECKED_CAST")
    fun objectAt(value: Any?, vararg path: String): Map<String, Any?>? {
        var current = value
        for (key in path) {
            current = (current as? Map<String, Any?>)?.get(key) ?: return null
        }
        return current as? Map<String, Any?>
    }

    fun text(value: Any?): String? = value as? String

    fun long(value: Any?): Long? = when (value) {
        is Long -> value
        is Double -> value.toLong()
        else -> null
    }

    fun double(value: Any?): Double? = when (value) {
        is Double -> value
        is Long -> value.toDouble()
        else -> null
    }

    /** Строку прочитать не удалось. Без стектрейса: это ожидаемый исход, а не авария. */
    class Malformed : Exception(null, null, false, false)

    private class Reader(private val s: String) {

        private var i = 0

        fun parseWhole(): Any? {
            val v = value()
            skipWs()
            if (i != s.length) throw Malformed()
            return v
        }

        private fun skipWs() {
            while (i < s.length && s[i].code <= ' '.code) i++
        }

        private fun peek(): Char {
            skipWs()
            if (i >= s.length) throw Malformed()
            return s[i]
        }

        private fun take(): Char {
            if (i >= s.length) throw Malformed()
            return s[i++]
        }

        private fun expect(c: Char) {
            if (peek() != c) throw Malformed()
            i++
        }

        private fun value(): Any? = when (peek()) {
            '"' -> string()
            '{' -> obj()
            '[' -> arr()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            else -> number()
        }

        private fun literal(word: String, result: Any?): Any? {
            if (!s.startsWith(word, i)) throw Malformed()
            i += word.length
            return result
        }

        private fun obj(): Map<String, Any?> {
            expect('{')
            val out = LinkedHashMap<String, Any?>()
            if (peek() == '}') { i++; return out }
            while (true) {
                val key = string()
                expect(':')
                out[key] = value()
                when (peek()) {
                    ',' -> i++
                    '}' -> { i++; return out }
                    else -> throw Malformed()
                }
            }
        }

        private fun arr(): List<Any?> {
            expect('[')
            val out = ArrayList<Any?>()
            if (peek() == ']') { i++; return out }
            while (true) {
                out.add(value())
                when (peek()) {
                    ',' -> i++
                    ']' -> { i++; return out }
                    else -> throw Malformed()
                }
            }
        }

        private fun string(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                val c = take()
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> sb.append(escaped())
                    c.code < 0x20 -> throw Malformed()
                    else -> sb.append(c)
                }
            }
        }

        private fun escaped(): Char = when (take()) {
            '"' -> '"'
            '\\' -> '\\'
            '/' -> '/'
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                if (i + 4 > s.length) throw Malformed()
                val hex = s.substring(i, i + 4)
                i += 4
                (hex.toIntOrNull(16) ?: throw Malformed()).toChar()
            }
            else -> throw Malformed()
        }

        private fun number(): Any {
            skipWs()
            val start = i
            if (i < s.length && (s[i] == '-' || s[i] == '+')) i++
            var digits = false
            var real = false
            loop@ while (i < s.length) {
                val c = s[i]
                when {
                    c in '0'..'9' -> { digits = true; i++ }
                    c == '.' -> { real = true; i++ }
                    c == 'e' || c == 'E' -> {
                        real = true
                        i++
                        if (i < s.length && (s[i] == '-' || s[i] == '+')) i++
                    }
                    else -> break@loop
                }
            }
            if (!digits) throw Malformed()
            val raw = s.substring(start, i)
            if (!real) raw.toLongOrNull()?.let { return it }
            return raw.toDoubleOrNull() ?: throw Malformed()
        }
    }
}
