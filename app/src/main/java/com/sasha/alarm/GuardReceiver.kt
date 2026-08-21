package com.sasha.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sasha.alarm.core.FailSafe
import com.sasha.alarm.platform.AlarmScheduling
import com.sasha.alarm.platform.AlarmStateStore
import com.sasha.alarm.platform.AndroidClock

/**
 * Сторож бэкапа. Живёт в процессе `:guard` (см. манифест).
 *
 * Смысл отдельного процесса: если основной процесс упал, завис или был убит системой,
 * снимать блок больше некому. Этот получатель поднимается системой с нуля и не зависит
 * ни от одного объекта основного процесса — только от файла состояния на диске.
 */
class GuardReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val store = AlarmStateStore(app)
        val run = store.read().run

        if (run == null) {
            Log.i(TAG, "сторож проснулся, тревоги уже нет")
            return
        }

        val now = AndroidClock.nowMillis()
        if (!FailSafe.expired(run, now)) {
            // Разбудили раньше срока — переставляем на остаток. Остаток считается от
            // «сейчас», поэтому перевод часов вперёд сокращает ожидание, а не удлиняет.
            val remaining = FailSafe.remainingMillis(run, now)
            Log.i(TAG, "срок ещё не вышел, осталось $remaining мс")
            AlarmScheduling.scheduleGuard(
                context = app,
                triggerAtMillis = run.deadlineMillis,
                timeoutMillis = remaining,
                receiver = GuardReceiver::class.java,
            )
            return
        }

        AlarmController.dismissByGuard(app)
    }

    private companion object {
        const val TAG = "GuardReceiver"
    }
}
