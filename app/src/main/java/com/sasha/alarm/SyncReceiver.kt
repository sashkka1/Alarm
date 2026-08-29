package com.sasha.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sasha.alarm.platform.AlarmScheduling
import com.sasha.alarm.platform.EventLog
import com.sasha.alarm.platform.LogSender
import com.sasha.alarm.platform.PhoneUsage
import java.util.Calendar
import java.util.concurrent.Executors

/**
 * Ежедневная отдача журнала компьютеру.
 *
 * Будится раз в сутки в обеденное окно — тогда же, когда компьютер поднимает приём.
 * Не достучались (ноутбук выключен, телефон вне дома) — не беда: журнал целиком
 * никуда не девается и уедет следующей попыткой, а компьютер отбросит уже известное.
 *
 * ⚠️ **Служебный таймер, а не будильник.** Ставится через `setExactAndAllowWhileIdle` —
 * `setAlarmClock` предназначен только для боевого будильника (P0 №4), и вешать на него
 * передачу файлов значило бы показывать владельцу системную иконку будильника на обед.
 *
 * ⚠️ Повторов у таймера нет: каждое срабатывание планирует следующее. Так расписание
 * переживает и перезагрузку, и перевод часов — время следующего пуска считается заново
 * от текущего момента.
 */
class SyncReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        schedule(app)

        val pending = goAsync()
        // Сеть в главном потоке Android запрещает сама, а держать приёмник живым дольше
        // десяти секунд нельзя — уложиться помогают короткие таймауты в LogSender.
        Executors.newSingleThreadExecutor().execute {
            try {
                // Сначала переписываем к себе историю использования: система хранит её
                // около недели и стирает молча. Только потом отдаём — чтобы свежий
                // снимок уехал этой же передачей.
                val now = System.currentTimeMillis()
                val spans = PhoneUsage.snapshot(app, now - SNAPSHOT_WINDOW_MS, now)
                Log.i(TAG, "снимок использования: отрезков $spans")

                // 🔴 Снимок пишется фоновой очередью, а отправка читает файлы. Без ожидания
                // свежие строки не успевали лечь и уезжали только следующей передачей —
                // на компьютере это выглядело как «телефоном сегодня не пользовались».
                // Разобрано 27.08.2026 по живым данным.
                EventLog(app).flush()

                val result = LogSender(app).send()
                Log.i(TAG, "передача журнала: $result")
            } catch (e: Exception) {
                Log.w(TAG, "передача не удалась", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SyncReceiver"

        /**
         * Запустить передачу с компьютера, не дожидаясь обеда:
         * ```
         * adb shell am broadcast -a com.sasha.alarm.SYNC_NOW -n com.sasha.alarm/.SyncReceiver
         * ```
         * Пара к `TEST_TRIGGER`: то же назначение — проверить работу по кабелю, не трогая
         * телефон руками. Ничего опасного не делает, поэтому и открыт наружу.
         */
        const val ACTION_NOW = "com.sasha.alarm.SYNC_NOW"

        /** Час, в который телефон отдаёт журнал. Совпадает с обеденным окном компьютера. */
        const val HOUR = 13

        /**
         * Окно снимка использования — двое суток, а не одни.
         *
         * Запас на пропущенный день: телефон мог быть вне дома, выключен или без сети.
         * Повторы ничего не стоят — компьютер отбрасывает уже известные строки сам.
         */
        private const val SNAPSHOT_WINDOW_MS = 2 * 24 * 60 * 60 * 1000L

        /** Поставить следующую передачу — на сегодня, если [HOUR] ещё не наступил, иначе на завтра. */
        fun schedule(context: Context) {
            val next = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, HOUR)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            AlarmScheduling.scheduleResume(
                context = context.applicationContext,
                triggerAtMillis = next.timeInMillis,
                receiver = SyncReceiver::class.java,
            )
            Log.i(TAG, "следующая передача журнала в ${next.time}")
        }
    }
}
