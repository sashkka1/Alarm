package com.sasha.alarm.platform

import android.app.usage.UsageEvents
import android.content.Context
import android.util.Log
import com.sasha.alarm.core.AudioSpans
import com.sasha.alarm.core.EventType
import com.sasha.alarm.core.LogEvent
import com.sasha.alarm.core.LogValue
import com.sasha.alarm.core.UsageSpans
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
 * человек пользовался, журналу знать незачем. Нужен только факт «телефоном пользовались
 * с и по».
 *
 * ⚠️ **Пользование считается по замку** — правило в `UsageSpans`, за тестами. Здесь
 * только перевод системных событий в его словарь: чтение системы — дело платформы,
 * решение «что считать сеансом» — дело ядра.
 */
object PhoneUsage {

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

    /**
     * Снять отрезки музыки и дописать в журнал.
     *
     * Владелец, 2026-09-02: «по вечерам делаю растяжку, параллельно слушаю музыку, но
     * иногда могу почитать во время растяжки — я бы не хотел включать это в пользование
     * телефоном». Отрезки уезжают на компьютер, и разбор статистики вычитает их из
     * пользования.
     *
     * ⚠️ **Слежения в фоне нет.** История `UsageStats` помнит запуск и остановку службы
     * переднего плана задним числом — значит музыка снимается тем же окном, что и
     * пользование, и не стоит ни батареи, ни живого процесса.
     *
     * ⚠️ **Отсев повторов — по началу отрезка, а не по паре.** Один и тот же сеанс на
     * соседних снимках приходит с одинаковым началом; сравнивать пару целиком было бы
     * достаточно только пока концы не плавают.
     *
     * @return сколько отрезков записано
     */
    fun music(context: Context, fromMillis: Long, toMillis: Long): Int {
        if (!Permissions.usageStatsAllowed(context)) return 0
        val spans = try {
            readMusic(context, fromMillis, toMillis)
        } catch (e: Exception) {
            Log.w(TAG, "музыка не прочиталась", e)
            return 0
        }

        val log = EventLog(context)
        val known = log.events(EventType.PHONE_AUDIO).mapNotNull { it.long("fromMillis") }.toSet()
        val fresh = spans.filter { it.from !in known }

        val zone = TimeZone.getDefault()
        for (span in fresh) {
            log.write(
                LogEvent(
                    // Датируется концом, как и пользование: сеанс принадлежит тем суткам,
                    // в которые он закончился.
                    at = span.to,
                    tzOffsetMinutes = zone.getOffset(span.to) / 60_000,
                    type = EventType.PHONE_AUDIO,
                    data = mapOf(
                        "fromMillis" to LogValue.of(span.from),
                        "toMillis" to LogValue.of(span.to),
                        "app" to LogValue.of(MUSIC_APP),
                    ),
                )
            )
        }
        return fresh.size
    }

    private fun readMusic(context: Context, fromMillis: Long, toMillis: Long): List<AudioSpans.Span> {
        val manager = context.getSystemService(android.app.usage.UsageStatsManager::class.java)
            ?: return emptyList()
        val events = manager.queryEvents(fromMillis, toMillis)
        val event = UsageEvents.Event()

        val marks = mutableListOf<AudioSpans.Event>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName != MUSIC_APP) continue
            val mark = when (event.eventType) {
                UsageEvents.Event.FOREGROUND_SERVICE_START -> AudioSpans.Mark.STARTED
                UsageEvents.Event.FOREGROUND_SERVICE_STOP -> AudioSpans.Mark.STOPPED
                else -> continue
            }
            marks.add(AudioSpans.Event(event.timeStamp, mark))
        }
        return AudioSpans.of(marks)
    }

    /**
     * Отрезки пользования: события замка и экрана из системы, сборка — в [UsageSpans].
     *
     * ⚠️ **Событий переднего плана (`ACTIVITY_*`) здесь больше нет.** Разбор живых данных
     * 29.08.2026: сплошной сеанс они рвали на куски с дырами между приложениями, а
     * секундные вспышки экрана от уведомлений считали пользованием. Владелец: нужен
     * период, а не список приложений.
     */
    private fun read(context: Context, fromMillis: Long, toMillis: Long): List<Pair<Long, Long>> {
        val manager = context.getSystemService(android.app.usage.UsageStatsManager::class.java)
            ?: return emptyList()
        val events = manager.queryEvents(fromMillis, toMillis)
        val event = UsageEvents.Event()

        val marks = mutableListOf<UsageSpans.Event>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val mark = when (event.eventType) {
                UsageEvents.Event.KEYGUARD_HIDDEN -> UsageSpans.Mark.UNLOCKED
                UsageEvents.Event.KEYGUARD_SHOWN -> UsageSpans.Mark.LOCKED
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageSpans.Mark.SCREEN_OFF
                else -> continue
            }
            marks.add(UsageSpans.Event(event.timeStamp, mark))
        }

        return UsageSpans.of(marks).map { it.from to it.to }
    }

    /**
     * Чем владелец слушает музыку (он сам, 2026-09-02: «я слушаю через Яндекс Музыку»).
     *
     * ⚠️ **Пакет один и назван явно, а не «любая чужая служба».** Служб переднего плана
     * при погашенном экране полно — навигатор, загрузки, подкасты, — и считать музыкой всё
     * подряд значило бы вычёркивать из пользования случайные вечера.
     */
    private const val MUSIC_APP = "ru.yandex.music"

    private const val TAG = "PhoneUsage"
}
