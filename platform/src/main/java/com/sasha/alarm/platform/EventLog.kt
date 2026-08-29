package com.sasha.alarm.platform

import android.content.Context
import android.util.Log
import com.sasha.alarm.core.LogCodec
import com.sasha.alarm.core.LogEvent
import com.sasha.alarm.core.LogValue
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Запись журнала событий на телефоне.
 *
 * Файл только дозаписывается, по файлу на календарный месяц. Записанное не правится и не
 * стирается: разбор живёт на компьютере, телефон здесь только копит.
 *
 * ⚠️ Читать журнал телефону всё же приходится — но ровно за одним: **узнать, что в нём
 * уже есть** ([events]). Иначе то, что снимается окном (история использования, экспорт
 * Sleep Cycle), ложится в файл заново при каждом сеансе, и журнал растёт сотнями строк в
 * сутки на пустом месте. Поймано на телефоне 2026-08-26: 678 строк `phone.usage`, из
 * которых различны 63.
 *
 * Три правила, из которых состоит весь класс:
 *
 * 1. **Никогда не бросает исключений.** Журнал пишется на пути «зазвонил → показан экран»,
 *    и переполненный диск или отозванное право обязаны стоить строки, а не тревоги (P0 №7).
 * 2. **Никогда не пишет в главном потоке.** Дозапись уходит в отдельный поток; вызывающему
 *    возвращается управление сразу.
 * 3. **Переживает второй процесс.** Сторож живёт в `:guard` и пишет в тот же каталог,
 *    поэтому дозапись идёт под блокировкой файла — иначе две строки однажды слипнутся.
 */
class EventLog(context: Context) {

    private val dir = File(context.filesDir, DIR)

    /** Записать событие. Возвращает управление немедленно. */
    fun write(type: String, data: Map<String, LogValue> = emptyMap()) {
        val now = System.currentTimeMillis()
        val offsetMinutes = TimeZone.getDefault().getOffset(now) / 60_000
        write(LogEvent(at = now, tzOffsetMinutes = offsetMinutes, type = type, data = data))
    }

    fun write(type: String, vararg data: Pair<String, LogValue>) = write(type, data.toMap())

    fun write(event: LogEvent) {
        val line = try {
            LogCodec.encode(event) + "\n"
        } catch (e: Exception) {
            Log.w(TAG, "событие не удалось закодировать: ${event.type}", e)
            return
        }
        writer.execute { append(line, event.at, event.tzOffsetMinutes) }
    }

    private fun append(line: String, at: Long, offsetMinutes: Int) {
        try {
            if (!dir.exists() && !dir.mkdirs()) return
            FileOutputStream(File(dir, fileName(at, offsetMinutes)), true).use { out ->
                // Блокировка на время дозаписи: тот же каталог пишет процесс :guard.
                out.channel.lock().use {
                    out.write(line.toByteArray(Charsets.UTF_8))
                }
            }
        } catch (e: Exception) {
            // Диск переполнен, каталог недоступен, файл заблокирован — потеря строки
            // допустима, остановка тревоги нет.
            Log.w(TAG, "строка журнала потеряна", e)
        }
    }

    /**
     * Всё, что уже записано, — только события нужного вида.
     *
     * Читается весь журнал целиком: он в сотню-другую килобайт и разбирается за доли
     * секунды, а держать отдельный указатель «докуда дошли» значит завести вторую правду,
     * которая однажды разойдётся с файлами.
     *
     * ⚠️ Вызывать **не из главного потока**: это чтение с диска.
     * ⚠️ Не бросает никогда — журнал недоступен, значит известного нет, и вызывающий
     * просто запишет всё заново. Лишняя строка дешевле потерянной.
     */
    fun events(type: String): List<LogEvent> = try {
        dir.listFiles { f: File -> f.isFile && f.name.endsWith(".jsonl") }
            .orEmpty()
            .flatMap { it.readLines() }
            .mapNotNull { LogCodec.decode(it) }
            .filter { it.type == type }
    } catch (e: Exception) {
        Log.w(TAG, "журнал не прочитался — считаю, что он пуст", e)
        emptyList()
    }

    /**
     * Дождаться, пока всё записанное действительно легло в файл.
     *
     * 🔴 **Без этого свежие строки уезжали на компьютер с опозданием на сутки.** Разобрано
     * 27.08.2026 по живым данным: снимок пользования телефоном снимается прямо перед
     * передачей, но [write] возвращает управление сразу, а строки дописывает фоновый поток.
     * `LogSender` читал файлы раньше, чем очередь до них дошла, — и снимок уезжал только
     * следующей передачей, то есть на другой день. На компьютере это выглядело так, будто
     * телефоном сегодня не пользовались вовсе.
     *
     * ⚠️ Ставится в ту же очередь, что и записи, — значит дожидается **всего**, что
     * поставлено раньше, а не только последней строки.
     *
     * ⚠️ Вызывать только с фонового потока и только перед чтением журнала целиком. На пути
     * «зазвонил → показан экран» ждать чего бы то ни было нельзя (P0 №7).
     *
     * @return успела ли очередь. `false` — значит читающему достанется журнал без
     *   последних строк; это плохо, но не страшно: они уедут следующей передачей.
     */
    fun flush(timeoutMs: Long = FLUSH_TIMEOUT_MS): Boolean = try {
        writer.submit { }.get(timeoutMs, TimeUnit.MILLISECONDS)
        true
    } catch (e: Exception) {
        Log.w(TAG, "очередь записи не успела за $timeoutMs мс", e)
        false
    }

    /** Имя файла по местному месяцу события, а не по UTC: месяц — понятие календарное. */
    private fun fileName(at: Long, offsetMinutes: Int): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.timeInMillis = at + offsetMinutes * 60_000L
        return "%04d-%02d.jsonl".format(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
    }

    companion object {
        private const val TAG = "EventLog"
        private const val DIR = "log"

        /**
         * Сколько ждать очередь записи в [flush].
         *
         * Приёмник передачи живёт около десяти секунд, и половину этого времени занимают
         * поиск компьютера и сама отдача. Две секунды — с запасом: очередь пишет строки
         * по килобайту, а не файлы.
         */
        private const val FLUSH_TIMEOUT_MS = 2_000L

        /**
         * Один поток на всё приложение: событий единицы в час, а очередь гарантирует,
         * что строки не перепутаются между собой внутри процесса.
         */
        private val writer = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "event-log").apply { isDaemon = true }
        }
    }
}
