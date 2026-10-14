package com.sasha.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sasha.alarm.platform.PhoneSteps
import java.util.concurrent.Executors

/**
 * Сырые строки шагомера Xiaomi в logcat — проверка по кабелю.
 *
 * ```
 * adb shell am broadcast -a com.sasha.alarm.STEPS_DUMP -n com.sasha.alarm/.StepsDumpReceiver --el from <мс> --el to <мс>
 * adb logcat -d -s StepsDump:V
 * ```
 *
 * ⚠️ Оболочка adb провайдер шагов не читает, а корзины журнала (тогда по 15 минут) прятали, где
 * началась и кончилась прогулка. Нужен был замер длины шага (владелец, 2026-09-13) — отсюда
 * этот приёмник. Ничего не пишет и не меняет, только печатает.
 *
 * ⚠️ Открыт наружу, но закрыт разрешением `DUMP`, как `HookCheckReceiver`: оно есть у
 * оболочки adb и нет у обычных приложений — чужой программе шаги владельца этим путём не достать.
 */
class StepsDumpReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val from = intent.getLongExtra(EXTRA_FROM, -1L)
        val to = intent.getLongExtra(EXTRA_TO, -1L)
        if (from < 0 || to <= from) {
            Log.w(TAG, "нужны --el from и --el to, from < to")
            return
        }
        val app = context.applicationContext
        val pending = goAsync()
        Executors.newSingleThreadExecutor().execute {
            try {
                val rows = PhoneSteps.rawRows(app, from, to).sortedBy { it.from }
                for (row in rows) Log.i(TAG, "${row.from} ${row.to} ${row.steps}")
                Log.i(TAG, "строк ${rows.size}")
            } catch (e: Exception) {
                Log.w(TAG, "шаги не прочитались", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "StepsDump"
        const val EXTRA_FROM = "from"
        const val EXTRA_TO = "to"
    }
}
