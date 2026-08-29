package com.sasha.alarm.platform

import android.app.usage.UsageEvents
import android.content.Context
import android.util.Log
import com.sasha.alarm.core.EventType
import com.sasha.alarm.core.LogEvent
import com.sasha.alarm.core.LogValue
import java.util.TimeZone

/**
 * Чем телефон был занят.
 *
 * Отвечает на главный вопрос утра после снятия тревоги: встал или лёг обратно. Три
 * исхода, и различить их можно только так — телефоном пользовались (залип в экран),
 * телефоном не пользовались и включился ноутбук (сел работать), не было ни того ни
 * другого (спит либо ушёл на улицу, и тогда должна быть уличная метка).
 *
 * ⚠️ **Систему приходится переписывать к себе.** Подробную историю Android хранит
 * около недели и молча стирает, поэтому снимок снимается раз в сутки — иначе всё,
 * что старше, исчезнет навсегда.
 *
 * ⚠️ Пишутся **отрезки**, а не отдельные запуски приложений: какими именно программами
 * человек пользовался, журналу знать незачем. Нужен только факт «экран был занят
 * с и по».
 */
object PhoneUsage {

    /** Отрезки короче этого — случайные касания, а не пользование. */
    private const val MIN_SPAN_MS = 30_000L

    /** Разрыв меньше этого не разрывает отрезок: переключение между приложениями. */
    private const val GLUE_MS = 60_000L

    /**
     * Снять историю за окно и дописать в журнал.
     *
     * ⚠️ **Пишутся только незнакомые отрезки.** Окно снимка шире суток, а сеансов за день
     * бывает несколько — без отсева один и тот же отрезок ложился бы в файл при каждом
     * сеансе. Поймано на телефоне 2026-08-26: 678 строк, из которых различны 63, по
     * тринадцать копий каждой. Компьютер повторы отбрасывал и статистику это не портило,
     * поэтому дефект и жил незаметно — но журнал телефона рос впустую.
     *
     * Тот же приём стоит на приёме экспорта Sleep Cycle (`ImportActivity`): всё, что
     * снимается окном, а не событием, обязано сверяться с уже записанным.
     *
     * @return сколько отрезков записано; 0 — доступа нет, нечего писать либо всё уже знакомо.
     */
    fun snapshot(context: Context, fromMillis: Long, toMillis: Long): Int {
        if (!Permissions.usageStatsAllowed(context)) return 0
        val spans = try {
            read(context, fromMillis, toMillis)
        } catch (e: Exception) {
            Log.w(TAG, "статистика использования не прочиталась", e)
            return 0
        }

        val log = EventLog(context)
        val known = log.events(EventType.PHONE_USAGE)
            .mapNotNull { event ->
                val from = event.long("fromMillis") ?: return@mapNotNull null
                val to = event.long("toMillis") ?: return@mapNotNull null
                from to to
            }
            .toSet()
        val fresh = spans.filter { it !in known }
        if (fresh.size < spans.size) {
            Log.i(TAG, "знакомых отрезков пропущено: ${spans.size - fresh.size}")
        }

        val zone = TimeZone.getDefault()
        for ((from, to) in fresh) {
            log.write(
                LogEvent(
                    // Событие датируется концом отрезка: так оно попадает в те сутки,
                    // в которые пользование закончилось, а не началось.
                    at = to,
                    tzOffsetMinutes = zone.getOffset(to) / 60_000,
                    type = EventType.PHONE_USAGE,
                    data = mapOf(
                        "fromMillis" to LogValue.of(from),
                        "toMillis" to LogValue.of(to),
                    ),
                )
            )
        }
        return fresh.size
    }

    /** Отрезки «экран был занят», склеенные из событий переднего плана. */
    private fun read(context: Context, fromMillis: Long, toMillis: Long): List<Pair<Long, Long>> {
        val manager = context.getSystemService(android.app.usage.UsageStatsManager::class.java)
            ?: return emptyList()
        val events = manager.queryEvents(fromMillis, toMillis)
        val event = UsageEvents.Event()

        val spans = mutableListOf<Pair<Long, Long>>()
        var openedAt: Long? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED ->
                    if (openedAt == null) openedAt = event.timeStamp

                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val start = openedAt ?: continue
                    spans.add(start to event.timeStamp)
                    openedAt = null
                }
            }
        }
        // ⚠️ Незакрытый отрезок (телефоном пользуются прямо сейчас) **не пишется вовсе**.
        // Записать его пришлось бы концом «сейчас», а завтрашний снимок увидел бы тот же
        // сеанс уже целиком и другой парой — то есть отсев по совпадению его не поймал бы,
        // и в статистику легли бы два наложенных отрезка вместо одного. Терять нечего:
        // окно снимка шире суток, и следующий сеанс подберёт этот отрезок законченным.

        return glue(spans).filter { (from, to) -> to - from >= MIN_SPAN_MS }
    }

    /**
     * Склеить соседние отрезки.
     *
     * Без этого переключение между приложениями рвало бы одно занятие на десяток
     * кусочков, и «пользовался 12 минут» превращалось бы в двенадцать записей по минуте.
     */
    private fun glue(spans: List<Pair<Long, Long>>): List<Pair<Long, Long>> {
        if (spans.isEmpty()) return spans
        val sorted = spans.sortedBy { it.first }
        val out = mutableListOf(sorted.first())
        for ((from, to) in sorted.drop(1)) {
            val (lastFrom, lastTo) = out.last()
            if (from - lastTo <= GLUE_MS) {
                out[out.lastIndex] = lastFrom to maxOf(lastTo, to)
            } else {
                out.add(from to to)
            }
        }
        return out
    }

    private const val TAG = "PhoneUsage"
}
